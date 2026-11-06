package io.tesla.maven.plugins.test;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class MultimoduleSkipInstallDeployTest extends AbstractIntegrationTest {

    public MultimoduleSkipInstallDeployTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
        super(verifierBuilder);
    }

    @Test
    public void testBasic() throws Exception {
        Path basedir = resources.getBasedir("multimodule-skip-install-deploy").toPath();

        Path remoterepo = basedir.resolve("remoterepo");
        Files.createDirectory(remoterepo);

        MavenExecutionResult result = verifier.forProject(basedir.toFile()) //
                .withCliOption("-Drepopath=" + remoterepo.toRealPath()) //
                .execute("deploy");

        result.assertErrorFreeLog();

        Path group = remoterepo.resolve("io/takari/lifecycle/its/multimodule-skip-install-deploy");
        Assert.assertTrue(Files.isReadable(group.resolve("parent/1.0/parent-1.0.pom")));
        Assert.assertTrue(Files.isReadable(group.resolve("modulea/1.0/modulea-1.0.pom")));
        Assert.assertTrue(Files.isReadable(group.resolve("modulea/1.0/modulea-1.0.jar")));

        Assert.assertFalse(Files.isReadable(group.resolve("moduleb/1.0/moduleb-1.0.pom")));
        Assert.assertFalse(Files.isReadable(group.resolve("moduleb/1.0/moduleb-1.0.jar")));
    }
}
