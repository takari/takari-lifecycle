package io.tesla.maven.plugins.test;

import static io.takari.maven.testing.TestResources.assertFilesPresent;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.Assert;
import org.junit.Test;

public class BasicTest extends AbstractIntegrationTest {

    public BasicTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
        super(verifierBuilder);
    }

    @Test
    public void testBasic() throws Exception {
        Path basedir = resources.getBasedir("basic").toPath();

        Path remoterepo = basedir.resolve("remoterepo");
        Files.createDirectory(remoterepo);

        Path localrepo = properties.getLocalRepository().toPath();

        MavenExecutionResult result = verifier.forProject(basedir.toFile()) //
                .withCliOption("-Drepopath=" + remoterepo.toRealPath()) //
                .withCliOption("-X") //
                .execute("deploy");

        result.assertErrorFreeLog();

        // TODO assert expected mojos were executed
        // TODO assertFileExist, etc
        // TODO assert jar content
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-1.0.jar")));
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-1.0-tests.jar")));

        Path localGroup = localrepo.resolve("io/takari/lifecycle/its/basic");
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic/1.0/basic-1.0.pom")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic/1.0/basic-1.0.jar")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic/1.0/basic-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic/1.0/basic-1.0-tests.jar")));

        Path remoteGroup = remoterepo.resolve("io/takari/lifecycle/its/basic");
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic/1.0/basic-1.0.pom")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic/1.0/basic-1.0.jar")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic/1.0/basic-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic/1.0/basic-1.0-tests.jar")));
    }

    @Test
    public void testBasicPlugin() throws Exception {
        Path basedir = resources.getBasedir("basic-plugin").toPath();

        Path remoterepo = basedir.resolve("remoterepo");
        Files.createDirectory(remoterepo);

        Path localrepo = properties.getLocalRepository().toPath();

        MavenExecutionResult result = verifier.forProject(basedir.toFile()) //
                .withCliOption("-Drepopath=" + remoterepo.toRealPath()) //
                .execute("deploy");

        result.assertErrorFreeLog();

        // TODO assert expected mojos were executed
        // TODO assertFileExist, etc
        // TODO assert jar content
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-plugin-1.0.jar")));
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-plugin-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-plugin-1.0-tests.jar")));

        Path localGroup = localrepo.resolve("io/takari/lifecycle/its/basic");
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-plugin/1.0/basic-plugin-1.0.pom")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-plugin/1.0/basic-plugin-1.0.jar")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-plugin/1.0/basic-plugin-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-plugin/1.0/basic-plugin-1.0-tests.jar")));

        Path remoteGroup = remoterepo.resolve("io/takari/lifecycle/its/basic");
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-plugin/1.0/basic-plugin-1.0.pom")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-plugin/1.0/basic-plugin-1.0.jar")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-plugin/1.0/basic-plugin-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-plugin/1.0/basic-plugin-1.0-tests.jar")));

        // assert m2e metadata
        Assert.assertEquals(
                readFileUTF8(basedir.resolve("src/main/m2e/lifecycle-mapping-metadata.xml")), //
                readZipFileEntryUTF8(
                        basedir.resolve("target/basic-plugin-1.0.jar"), "META-INF/m2e/lifecycle-mapping-metadata.xml"));
    }

    private String readZipFileEntryUTF8(Path zipFile, String entryPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile());
                InputStream is = zip.getInputStream(zip.getEntry(entryPath))) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readFileUTF8(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    public void testBasicBuilder() throws Exception {
        Path basedir = resources.getBasedir("basic-builder").toPath();

        Path remoterepo = basedir.resolve("remoterepo");
        Files.createDirectory(remoterepo);

        Path localrepo = properties.getLocalRepository().toPath();

        MavenExecutionResult result = verifier.forProject(basedir.toFile()) //
                .withCliOption("-Drepopath=" + remoterepo.toRealPath()) //
                .withCliOption("-Dincrementalbuild.version=" + properties.get("incrementalbuild.version")) //
                .execute("deploy");

        result.assertErrorFreeLog();

        // TODO assert expected mojos were executed
        // TODO assertFileExist, etc
        // TODO assert jar content
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-builder-1.0.jar")));
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-builder-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-builder-1.0-tests.jar")));
        Assert.assertTrue(
                Files.isReadable(basedir.resolve("target/classes/META-INF/m2e/lifecycle-mapping-metadata.xml")));

        Path localGroup = localrepo.resolve("io/takari/lifecycle/its/basic");
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-builder/1.0/basic-builder-1.0.pom")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-builder/1.0/basic-builder-1.0.jar")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-builder/1.0/basic-builder-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-builder/1.0/basic-builder-1.0-tests.jar")));

        Path remoteGroup = remoterepo.resolve("io/takari/lifecycle/its/basic");
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-builder/1.0/basic-builder-1.0.pom")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-builder/1.0/basic-builder-1.0.jar")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-builder/1.0/basic-builder-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-builder/1.0/basic-builder-1.0-tests.jar")));
    }

    @Test
    public void testBasicComponent() throws Exception {
        Path basedir = resources.getBasedir("basic-component").toPath();

        Path remoterepo = basedir.resolve("remoterepo");
        Files.createDirectory(remoterepo);

        Path localrepo = properties.getLocalRepository().toPath();

        MavenExecutionResult result = verifier.forProject(basedir.toFile()) //
                .withCliOption("-Drepopath=" + remoterepo.toRealPath()) //
                .execute("deploy");

        result.assertErrorFreeLog();

        assertFilesPresent(
                basedir.toFile(), //
                "target/classes/META-INF/sisu/javax.inject.Named", //
                "target/test-classes/META-INF/sisu/javax.inject.Named", //
                "target/test-classes/test.properties");

        // TODO assert expected mojos were executed
        // TODO assertFileExist, etc
        // TODO assert jar content
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-component-1.0.jar")));
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-component-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/basic-component-1.0-tests.jar")));

        Path localGroup = localrepo.resolve("io/takari/lifecycle/its/basic");
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-component/1.0/basic-component-1.0.pom")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-component/1.0/basic-component-1.0.jar")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-component/1.0/basic-component-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(localGroup.resolve("basic-component/1.0/basic-component-1.0-tests.jar")));

        Path remoteGroup = remoterepo.resolve("io/takari/lifecycle/its/basic");
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-component/1.0/basic-component-1.0.pom")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-component/1.0/basic-component-1.0.jar")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-component/1.0/basic-component-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("basic-component/1.0/basic-component-1.0-tests.jar")));
    }
}
