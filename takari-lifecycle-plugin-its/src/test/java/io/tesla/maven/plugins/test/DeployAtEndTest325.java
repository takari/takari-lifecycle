package io.tesla.maven.plugins.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;

@MavenVersions({"3.2.5"})
public class DeployAtEndTest325 extends AbstractIntegrationTest {

  public DeployAtEndTest325(MavenRuntimeBuilder verifierBuilder) throws Exception {
    super(verifierBuilder);
  }

  @Test
  public void test325() throws Exception {
    File basedir = resources.getBasedir("multimodule-deploy-at-end");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    MavenExecutionResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .execute("deploy");

    result.assertLogText("[ERROR]");
    result.assertLogText("Deploy-at-end is not supported on maven versions <3.3.1");

    File group = new File(remoterepo, "io/takari/lifecycle/its/multimodule-deploy-at-end");
    Assert.assertFalse(new File(group, "parent/1.0/parent-1.0.pom").canRead());
    Assert.assertFalse(new File(group, "modulea/1.0/modulea-1.0.pom").canRead());
    Assert.assertFalse(new File(group, "modulea/1.0/modulea-1.0.jar").canRead());
    Assert.assertFalse(new File(group, "moduleb/1.0/moduleb-1.0.pom").canRead());
    Assert.assertFalse(new File(group, "moduleb/1.0/moduleb-1.0.jar").canRead());
  }

}
