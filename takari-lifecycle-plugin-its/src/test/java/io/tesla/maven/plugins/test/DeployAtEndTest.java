package io.tesla.maven.plugins.test;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

// TODO: This IT is not running with Maven 4 due following: embedded uses "magic" method, not present in maven 4
// anymore, so forked is must to be used, but alas, forked throws on (even expected) errors, and does not return result
// as one would expect.
@MavenVersions({"3.9.13", "3.9.16"})
public class DeployAtEndTest extends AbstractIntegrationTest {

    public DeployAtEndTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
        super(verifierBuilder, false);
    }

    @Test
    public void testGood() throws Exception {
        Path basedir = resources.getBasedir("multimodule-deploy-at-end").toPath();

        Path remoterepo = basedir.resolve("remoterepo");
        Files.createDirectory(remoterepo);

        MavenExecutionResult result = verifier.forProject(basedir.toFile()) //
                .withCliOption("-Drepopath=" + remoterepo.toRealPath()) //
                .execute("deploy");

        result.assertErrorFreeLog();
        result.assertLogText("Performing deploy at end");

        Path group = remoterepo.resolve("io/takari/lifecycle/its/multimodule-deploy-at-end");
        Assert.assertTrue(Files.isReadable(group.resolve("parent/1.0/parent-1.0.pom")));
        Assert.assertTrue(Files.isReadable(group.resolve("modulea/1.0/modulea-1.0.pom")));
        Assert.assertTrue(Files.isReadable(group.resolve("modulea/1.0/modulea-1.0.jar")));
        Assert.assertTrue(Files.isReadable(group.resolve("moduleb/1.0/moduleb-1.0.pom")));
        Assert.assertTrue(Files.isReadable(group.resolve("moduleb/1.0/moduleb-1.0.jar")));
    }

    @Test
    public void testBad() throws Exception {
        Path basedir = resources.getBasedir("multimodule-deploy-at-end-bad").toPath();

        Path remoterepo = basedir.resolve("remoterepo");
        Files.createDirectory(remoterepo);

        MavenExecutionResult result = verifier.forProject(basedir.toFile()) //
                .withCliOption("-Drepopath=" + remoterepo.toRealPath()) //
                .execute("deploy");

        result.assertLogText("[ERROR]");
        result.assertLogText("Basic.java:[10,24] <identifier> expected");
        result.assertLogText("Not performing deploy at end due to errors");

        Path group = remoterepo.resolve("io/takari/lifecycle/its/multimodule-deploy-at-end-bad");
        Assert.assertFalse(Files.isReadable(group.resolve("parent/1.0/parent-1.0.pom")));
        Assert.assertFalse(Files.isReadable(group.resolve("modulea/1.0/modulea-1.0.pom")));
        Assert.assertFalse(Files.isReadable(group.resolve("modulea/1.0/modulea-1.0.jar")));
        Assert.assertFalse(Files.isReadable(group.resolve("moduleb/1.0/moduleb-1.0.pom")));
        Assert.assertFalse(Files.isReadable(group.resolve("moduleb/1.0/moduleb-1.0.jar")));
    }
}
