package io.tesla.maven.plugins.test;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Test;

public class PomPackagingTest extends AbstractIntegrationTest {
    public PomPackagingTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
        super(verifierBuilder);
    }

    @Test
    public void testPomPackaging() throws Exception {
        Path basedir = resources.getBasedir("pom-packaging").toPath();

        Path remoterepo = basedir.resolve("remoterepo");
        Files.createDirectory(remoterepo);

        Path localrepo = properties.getLocalRepository().toPath();

        MavenExecutionResult result = verifier.forProject(basedir.toFile()) //
                .withCliOption("-Drepopath=" + remoterepo.toRealPath()) //
                .execute("deploy");
        result.assertErrorFreeLog();

        Path localGroup = localrepo.resolve("io/takari/lifecycle/its/pom-packaging");
        Assert.assertTrue(Files.isReadable(localGroup.resolve("1.0/pom-packaging-1.0.pom")));

        Path remoteGroup = remoterepo.resolve("io/takari/lifecycle/its/pom-packaging");
        Assert.assertTrue(Files.isReadable(remoteGroup.resolve("1.0/pom-packaging-1.0.pom")));
    }
}
