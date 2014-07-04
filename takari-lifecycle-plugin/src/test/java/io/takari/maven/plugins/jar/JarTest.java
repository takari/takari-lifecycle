package io.takari.maven.plugins.jar;

import static org.apache.maven.plugin.testing.MojoParameters.newParameter;
import static org.apache.maven.plugin.testing.resources.TestResources.cp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import io.takari.hash.FingerprintSha1Streaming;
import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.io.Files;

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
    File basedir = resources.getBasedir("jar/project-with-resources");
    mojos.executeMojo(basedir, "process-resources");
    File resource = new File(basedir, "target/classes/resource.txt");
    assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    assertTrue(line.contains("resource.txt"));
    //
    // Generate the JAR a first time and capture the fingerprint
    //
    mojos.executeMojo(basedir, "jar");
    File jar0 = new File(basedir, "target/test-1.0.jar");
    assertTrue(jar0.exists());
    String fingerprint0 = new FingerprintSha1Streaming().fingerprint(jar0);
    //
    // Generate the JAR a second time and ensure that the fingerprint is still the same when
    // the JAR content is the same. The outer SHA1 of a JAR built at two points in time will
    // be different even though the content has not changed.
    //
    mojos.executeMojo(basedir, "jar");
    File jar1 = new File(basedir, "target/test-1.0.jar");
    Assert.assertTrue(jar1.exists());
    String fingerprint1 = new FingerprintSha1Streaming().fingerprint(jar1);
    assertEquals("We expect the JAR to have the same fingerprint after repeated builds.", fingerprint0, fingerprint1);

    // Make sure our maven properties file is written correctly
    ZipFile zip0 = new ZipFile(jar1);
    try {
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
    } finally {
      zip0.close();
    }

    ZipFile zip1 = new ZipFile(jar1);
    try {
      String manifestEntryName = "META-INF/MANIFEST.MF";
      ZipEntry manifestEntry = zip1.getEntry(manifestEntryName);
      if (manifestEntry != null) {
        InputStream is = zip1.getInputStream(manifestEntry);
        Manifest p = new Manifest(is);
        assertNotNull(p.getMainAttributes().getValue("Built-By"));
        assertNotNull(p.getMainAttributes().getValue("Build-Jdk"));
        assertEquals("1.0", p.getMainAttributes().getValue("Manifest-Version"));
        assertEquals("test", p.getMainAttributes().getValue("Implementation-Title"));
        assertEquals("1.0", p.getMainAttributes().getValue("Implementation-Version"));
        assertEquals("io.takari.lifecycle.its", p.getMainAttributes().getValue("Implementation-Vendor-Id"));
      } else {
        fail("We expected the standard META-INF/MANIFEST.MF");
      }
    } finally {
      zip1.close();
    }
  }

  @Test
  public void testBasic_attachedArtifacts() throws Exception {
    File basedir = resources.getBasedir("jar/basic");
    cp(basedir, "src/main/resources/resource.txt", "target/classes/resource.txt");
    cp(basedir, "src/test/resources/test-resource.txt", "target/test-classes/resource.txt");

    MavenProject project = mojos.readMavenProject(basedir);
    mojos.executeMojo(project, "jar", newParameter("sourceJar", "true"), newParameter("testJar", "true"));

    Map<String, Artifact> attachedArtifacts = new HashMap<String, Artifact>();
    for (Artifact artifact : project.getAttachedArtifacts()) {
      assertNull(attachedArtifacts.put(artifact.getClassifier(), artifact));
    }

    // main artifact (test-1.0.jar)
    File jar = new File(basedir, "target/test-1.0.jar");
    assertTrue(jar.exists());
    assertEquals(jar, project.getArtifact().getFile());
    assertEquals("jar", project.getArtifact().getType());

    // sources (test-1.0-sources.jar)
    File sourceJar = new File(basedir, "target/test-1.0-sources.jar");
    assertTrue(sourceJar.exists());
    assertEquals(sourceJar, attachedArtifacts.get("sources").getFile());
    assertEquals("jar", attachedArtifacts.get("sources").getType());

    // tests (test-1.0-tests.jar)
    File testJar = new File(basedir, "target/test-1.0-tests.jar");
    assertTrue(testJar.exists());
    assertEquals(testJar, attachedArtifacts.get("tests").getFile());
    assertEquals("jar", attachedArtifacts.get("tests").getType());
  }

  @Test
  public void testClassloader_getResources() throws Exception {
    File basedir = resources.getBasedir("jar/project-with-resources");
    mojos.executeMojo(basedir, "process-resources");
    mojos.executeMojo(basedir, "jar");
    File jar = new File(basedir, "target/test-1.0.jar");
    try (URLClassLoader cl = new URLClassLoader(new URL[] {jar.toURI().toURL()}, null)) {
      List<URL> list = toList(cl.getResources("subdir"));
      Assert.assertEquals(1, list.size());
      Assert.assertTrue(list.get(0).toString(), list.get(0).toString().endsWith("test-1.0.jar!/subdir"));
    }
  }

  @Test
  public void testJarEntries() throws Exception {
    File basedir = resources.getBasedir("jar/project-with-resources");
    mojos.executeMojo(basedir, "process-resources");
    mojos.executeMojo(basedir, "jar");
    File jar = new File(basedir, "target/test-1.0.jar");
    try (ZipFile zip = new ZipFile(jar)) {
      TreeMap<String, ZipEntry> sorted = new TreeMap<>();

      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        sorted.put(entry.getName(), entry);
      }

      StringBuilder actual = new StringBuilder();
      for (ZipEntry entry : sorted.values()) {
        actual.append(entry.isDirectory() ? "D " : "F ");
        actual.append(entry.getName()).append(' ').append(entry.getSize());
        actual.append('\n');
      }

      StringBuilder expected = new StringBuilder();
      expected.append("D META-INF/ 0\n");
      expected.append("F META-INF/MANIFEST.MF 287\n");
      expected.append("D META-INF/maven/ 0\n");
      expected.append("D META-INF/maven/io.takari.lifecycle.its/ 0\n");
      expected.append("D META-INF/maven/io.takari.lifecycle.its/test/ 0\n");
      expected.append("F META-INF/maven/io.takari.lifecycle.its/test/pom.properties 60\n");
      expected.append("F resource.txt 12\n");
      expected.append("D subdir/ 0\n");
      expected.append("F subdir/resource.txt 19\n");

      Assert.assertEquals(expected.toString(), actual.toString());
    }
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
    File basedir = resources.getBasedir("jar/project-with-manifest");
    new File(basedir, "target/classes").mkdirs(); // TODO this shouldn't be necessary
    mojos.executeMojo(basedir, "jar");
    JarFile jar = new JarFile(new File(basedir, "target/test-1.0.jar"));
    try {
      Manifest mf = jar.getManifest();
      Assert.assertEquals("custom-value", mf.getMainAttributes().getValue("Custom-Entry"));
    } finally {
      jar.close();
    }
  }

  @Test
  public void testDuplicateCustomManifest() throws Exception {
    File basedir = resources.getBasedir("jar/project-with-manifest-under-target-classes");
    mojos.executeMojo(basedir, "jar");
    JarFile jar = new JarFile(new File(basedir, "target/test-1.0.jar"));
    try {
      // make sure there is only one manifest entry
      int count = 0;
      for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
        JarEntry entry = entries.nextElement();
        if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entry.getName())) {
          count++;
        }
      }
      Assert.assertEquals(1, count);
      // now check the manifest contents
      Manifest mf = jar.getManifest();
      Assert.assertEquals("custom-value", mf.getMainAttributes().getValue("Custom-Entry"));
    } finally {
      jar.close();
    }
  }

  @Test
  public void testEmpty() throws Exception {
    File basedir = resources.getBasedir("jar/empty");
    mojos.executeMojo(basedir, "jar");

    assertTrue(new File(basedir, "target/test-1.0.jar").exists());
    assertTrue(new File(basedir, "target/test-1.0-sources.jar").exists());
    assertFalse(new File(basedir, "target/test-1.0-tests.jar").exists());
  }
}
