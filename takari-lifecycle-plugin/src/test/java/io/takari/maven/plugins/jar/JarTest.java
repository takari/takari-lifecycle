package io.takari.maven.plugins.jar;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.create;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Os;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class JarTest {

    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final IncrementalBuildRule mojos = new IncrementalBuildRule();

    @Test
    public void jarCreation() throws Exception {
        //
        // Generate some resources to JAR
        //
        Path basedir = resources.getBasedir("jar/project-with-resources").toPath();
        mojos.executeMojo(basedir.toFile(), "process-resources");
        Path resource = basedir.resolve("target/classes/resource.txt");
        assertTrue(Files.exists(resource));
        String line = Files.readAllLines(resource, Charset.defaultCharset()).get(0);
        assertTrue(line.contains("resource.txt"));
        //
        // Generate the JAR a first time and capture the fingerprint
        //
        mojos.executeMojo(basedir.toFile(), "jar");
        Path jar0 = basedir.resolve("target/test-1.0.jar");
        assertTrue(Files.exists(jar0));
        String fingerprint0 = fingerprint(jar0);
        //
        // Generate the JAR a second time and ensure that the fingerprint is still the same when
        // the JAR content is the same. The outer SHA1 of a JAR built at two points in time will
        // be different even though the content has not changed.
        //
        mojos.executeMojo(basedir.toFile(), "jar");
        Path jar1 = basedir.resolve("target/test-1.0.jar");
        Assert.assertTrue(Files.exists(jar1));
        String fingerprint1 = fingerprint(jar1);
        assertEquals(
                "We expect the JAR to have the same fingerprint after repeated builds.", fingerprint0, fingerprint1);

        // Make sure our maven properties file is written correctly
        try (ZipFile zip0 = new ZipFile(jar1.toFile())) {
            String pomProperties = "META-INF/maven/io.takari.lifecycle.its/test/pom.properties";
            ZipEntry entry = zip0.getEntry(pomProperties);
            if (entry != null) {
                InputStream is = zip0.getInputStream(entry);
                Properties p = new Properties();
                p.load(is);
                assertEquals("io.takari.lifecycle.its", p.getProperty("groupId"));
                assertEquals("test", p.getProperty("artifactId"));
                assertEquals("1.0", p.getProperty("version"));
            } else {
                fail("We expected the standard pom.properties: " + pomProperties);
            }
        }

        try (ZipFile zip1 = new ZipFile(jar1.toFile())) {
            String manifestEntryName = "META-INF/MANIFEST.MF";
            ZipEntry manifestEntry = zip1.getEntry(manifestEntryName);
            if (manifestEntry != null) {
                InputStream is = zip1.getInputStream(manifestEntry);
                Manifest p = new Manifest(is);
                assertEquals("1.0", p.getMainAttributes().getValue("Manifest-Version"));
                assertEquals("test", p.getMainAttributes().getValue("Implementation-Title"));
                assertEquals("1.0", p.getMainAttributes().getValue("Implementation-Version"));
                assertEquals("io.takari.lifecycle.its", p.getMainAttributes().getValue("Implementation-Vendor-Id"));
            } else {
                fail("We expected the standard META-INF/MANIFEST.MF");
            }
        }
    }

    @Test
    public void testBasic_attachedArtifacts() throws Exception {
        Path basedir = resources.getBasedir("jar/basic").toPath();
        cp(basedir.toFile(), "src/main/resources/resource.txt", "target/classes/resource.txt");
        cp(basedir.toFile(), "src/test/resources/test-resource.txt", "target/test-classes/resource.txt");

        MavenProject project = mojos.readMavenProject(basedir.toFile());
        mojos.executeMojo(project, "jar", newParameter("sourceJar", "true"), newParameter("testJar", "true"));

        Map<String, Artifact> attachedArtifacts = new HashMap<String, Artifact>();
        for (Artifact artifact : project.getAttachedArtifacts()) {
            assertNull(attachedArtifacts.put(artifact.getClassifier(), artifact));
        }

        // main artifact (test-1.0.jar)
        Path jar = basedir.resolve("target/test-1.0.jar");
        assertTrue(Files.exists(jar));
        assertEquals(jar, project.getArtifact().getFile().toPath());
        assertEquals("jar", project.getArtifact().getType());

        // sources (test-1.0-sources.jar)
        Path sourceJar = basedir.resolve("target/test-1.0-sources.jar");
        assertTrue(Files.exists(sourceJar));
        assertEquals(sourceJar, attachedArtifacts.get("sources").getFile().toPath());
        assertEquals("jar", attachedArtifacts.get("sources").getType());

        // tests (test-1.0-tests.jar)
        Path testJar = basedir.resolve("target/test-1.0-tests.jar");
        assertTrue(Files.exists(testJar));
        assertEquals(testJar, attachedArtifacts.get("tests").getFile().toPath());
        assertEquals("jar", attachedArtifacts.get("tests").getType());
    }

    @Test
    public void testClassloader_getResources() throws Exception {
        Path basedir = resources.getBasedir("jar/project-with-resources").toPath();
        mojos.executeMojo(basedir.toFile(), "process-resources");
        mojos.executeMojo(basedir.toFile(), "jar");
        Path jar = basedir.resolve("target/test-1.0.jar");
        try (URLClassLoader cl = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            List<URL> list = toList(cl.getResources("subdir"));
            Assert.assertEquals(1, list.size());
            Assert.assertTrue(list.get(0).toString(), list.get(0).toString().endsWith("test-1.0.jar!/subdir"));
        }
    }

    @Test
    public void testJarEntries() throws Exception {
        Path basedir = resources.getBasedir("jar/project-with-resources").toPath();
        mojos.executeMojo(basedir.toFile(), "process-resources");
        mojos.executeMojo(basedir.toFile(), "jar");
        Path jar = basedir.resolve("target/test-1.0.jar");
        assertZipEntries(
                jar //
                ,
                "D META-INF/ 315561600000" //
                ,
                "F META-INF/MANIFEST.MF 315561600000" //
                ,
                "D META-INF/maven/ 315561600000" //
                ,
                "D META-INF/maven/io.takari.lifecycle.its/ 315561600000" //
                ,
                "D META-INF/maven/io.takari.lifecycle.its/test/ 315561600000" //
                ,
                "F META-INF/maven/io.takari.lifecycle.its/test/pom.properties 315561600000" //
                ,
                "F resource.txt 315561600000" //
                ,
                "D subdir/ 315561600000" //
                ,
                "F subdir/resource.txt 315561600000" //
                );
    }

    private static void assertZipEntries(Path zipFile, String... expected) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Map<String, ZipEntry> sorted = new LinkedHashMap<>();

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                sorted.put(entry.getName(), entry);
            }

            StringBuilder actualSb = new StringBuilder();
            for (ZipEntry entry : sorted.values()) {
                actualSb.append(entry.isDirectory() ? "D " : "F ")
                        .append(entry.getName())
                        .append(' ')
                        .append(dosToJavaTime(entry.getTime()))
                        .append('\n');
            }

            StringBuilder expectedSb = new StringBuilder();
            for (String str : expected) {
                expectedSb.append(str).append('\n');
            }

            Assert.assertEquals(expectedSb.toString(), actualSb.toString());
        }
    }

    /**
     * The method {@link ZipEntry#getTime()} "adjusts" the DOS timestamp ("local time") to Java time ("UTC") by using
     * "default timezone" of the user. This means, that the method returns different timestamps in different Timezones.
     * while the value to assert against is Java UTC time. Meaning, test would pass in some TZ but fail in others.
     * Same applies to DST as well. This method merely "undoes" the adjustment, making it into UTC.
     */
    private static long dosToJavaTime(long time) {
        Calendar cal = Calendar.getInstance(TimeZone.getDefault(), Locale.ROOT);
        cal.setTimeInMillis(time);
        return time + ((cal.get(Calendar.ZONE_OFFSET) + (cal.get(Calendar.DST_OFFSET))));
    }

    private static <T> List<T> toList(Enumeration<T> e) {
        ArrayList<T> l = new ArrayList<T>();
        while (e.hasMoreElements()) {
            l.add(e.nextElement());
        }
        return l;
    }

    @Test
    public void testCustomManifest() throws Exception {
        Path basedir = resources.getBasedir("jar/project-with-manifest").toPath();
        Files.createDirectories(basedir.resolve("target/classes")); // TODO this shouldn't be necessary
        mojos.executeMojo(basedir.toFile(), "jar");
        try (JarFile jar = new JarFile(basedir.resolve("target/test-1.0.jar").toFile())) {
            Manifest mf = jar.getManifest();
            Assert.assertEquals("custom-value", mf.getMainAttributes().getValue("Custom-Entry"));
        }
    }

    @Test
    @Ignore("this is currently broken, but the fix requires changes to incrementalbuild")
    public void testCustomManifest_incremental() throws Exception {
        Path basedir = resources.getBasedir("jar/project-with-manifest").toPath();
        Files.createDirectories(basedir.resolve("target/classes")); // TODO this shouldn't be necessary
        mojos.executeMojo(basedir.toFile(), "jar");
        try (JarFile jar = new JarFile(basedir.resolve("target/test-1.0.jar").toFile())) {
            Manifest mf = jar.getManifest();
            Assert.assertEquals("custom-value", mf.getMainAttributes().getValue("Custom-Entry"));
        }

        cp(basedir.toFile(), "src/META-INF/MANIFEST.MF-changed", "src/META-INF/MANIFEST.MF");
        mojos.executeMojo(basedir.toFile(), "jar");
        try (JarFile jar = new JarFile(basedir.resolve("target/test-1.0.jar").toFile())) {
            Manifest mf = jar.getManifest();
            Assert.assertEquals("changed-custom-value", mf.getMainAttributes().getValue("Custom-Entry"));
        }
    }

    @Test
    public void testDuplicateCustomManifest() throws Exception {
        Path basedir = resources
                .getBasedir("jar/project-with-manifest-under-target-classes")
                .toPath();
        mojos.executeMojo(basedir.toFile(), "jar");
        try (JarFile jar = new JarFile(basedir.resolve("target/test-1.0.jar").toFile())) {
            // make sure there is only one manifest entry
            int count = 0;
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entry.getName())) {
                    count++;
                }
            }
            Assert.assertEquals(1, count);
            // now check the manifest contents
            Manifest mf = jar.getManifest();
            Assert.assertEquals("custom-value", mf.getMainAttributes().getValue("Custom-Entry"));
        }
    }

    @Test
    public void testCustomManifestEntries() throws Exception {
        Path basedir = resources.getBasedir("jar/project-with-manifest-entries").toPath();
        Files.createDirectories(basedir.resolve("target/classes")); // TODO this shouldn't be necessary
        mojos.executeMojo(basedir.toFile(), "jar");
        try (JarFile jar = new JarFile(basedir.resolve("target/test-1.0.jar").toFile())) {
            Manifest mf = jar.getManifest();
            Attributes main = mf.getMainAttributes();
            assertEquals("1.0", main.getValue("Manifest-Version"));
            assertEquals("test", main.getValue("Implementation-Title"));
            assertEquals("1.0", main.getValue("Implementation-Version"));
            assertEquals("io.takari.lifecycle.its", main.getValue("Implementation-Vendor-Id"));
            assertEquals("custom-value", main.getValue("Custom-Entry"));
        }
    }

    @Test
    public void testEmpty() throws Exception {
        Path basedir = resources.getBasedir("jar/empty").toPath();
        mojos.executeMojo(basedir.toFile(), "jar");

        assertTrue(Files.exists(basedir.resolve("target/test-1.0.jar")));
        assertTrue(Files.exists(basedir.resolve("target/test-1.0-sources.jar")));
        assertFalse(Files.exists(basedir.resolve("target/test-1.0-tests.jar")));
    }

    @Test
    public void testSymlinkedDirectories() throws Exception {
        Assume.assumeTrue(Os.isFamily(Os.FAMILY_UNIX));

        Path basedir = resources.getBasedir().toPath();

        Path orig = basedir.resolve("orig");
        create(orig.toFile(), "src/main/java/pkg/Class.java");
        create(orig.toFile(), "src/test/java/testpkg/Test.java");
        create(orig.toFile(), "target/classes/resource.txt", "target/classes/subdir/resource.txt");
        Files.createSymbolicLink(
                orig.resolve("target/classes/symlinked-resource.txt"), orig.resolve("target/classes/resource.txt"));
        create(orig.toFile(), "target/test-classes/test-resource.txt");

        Path symlink = Files.createSymbolicLink(basedir.resolve("symlink"), orig);
        mojos.executeMojo(symlink.toFile(), "jar", newParameter("testJar", "true"), newParameter("sourceJar", "true"));
        assertZipEntries(
                symlink.resolve("target/test-1.jar") //
                ,
                "D META-INF/ 315561600000" //
                ,
                "F META-INF/MANIFEST.MF 315561600000" //
                ,
                "D META-INF/maven/ 315561600000" //
                ,
                "D META-INF/maven/test/ 315561600000" //
                ,
                "D META-INF/maven/test/test/ 315561600000" //
                ,
                "F META-INF/maven/test/test/pom.properties 315561600000" //
                ,
                "F resource.txt 315561600000" //
                ,
                "D subdir/ 315561600000" //
                ,
                "F subdir/resource.txt 315561600000" //
                ,
                "F symlinked-resource.txt 315561600000" //
                );
        assertZipEntries(
                symlink.resolve("target/test-1-tests.jar") //
                ,
                "D META-INF/ 315561600000" //
                ,
                "F META-INF/MANIFEST.MF 315561600000" //
                ,
                "F test-resource.txt 315561600000" //
                );
        assertZipEntries(
                symlink.resolve("target/test-1-sources.jar") //
                ,
                "D META-INF/ 315561600000" //
                ,
                "F META-INF/MANIFEST.MF 315561600000" //
                ,
                "D pkg/ 315561600000" //
                ,
                "F pkg/Class.java 315561600000" //
                );
    }

    private static String fingerprint(Path file) throws IOException {
        Hasher hasher = Hashing.sha1().newHasher();
        byte[] buffer = new byte[30 * 1024];
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry != null) {
                    if (!entry.isDirectory()) {
                        try (InputStream in = new BufferedInputStream(zip.getInputStream(entry))) {
                            for (int count; (count = in.read(buffer)) != -1; ) {
                                hasher.putBytes(buffer, 0, count);
                            }
                        }
                    }
                }
            }
        }
        return BaseEncoding.base16().encode(hasher.hash().asBytes()).toLowerCase();
    }
}
