package io.tesla.maven.plugins.test;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;

@MavenVersions({"3.3.9", "3.5.0"})
public class DeployAtEndTest extends AbstractIntegrationTest {

  public DeployAtEndTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
    super(verifierBuilder);
  }

  @Test
  public void testGood() throws Exception {
    File basedir = resources.getBasedir("multimodule-deploy-at-end");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    MavenExecutionResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .execute("deploy");

    result.assertErrorFreeLog();
    result.assertLogText("Performing deploy at end");

    File group = new File(remoterepo, "io/takari/lifecycle/its/multimodule-deploy-at-end");
    Assert.assertTrue(new File(group, "parent/1.0/parent-1.0.pom").canRead());
    Assert.assertTrue(new File(group, "modulea/1.0/modulea-1.0.pom").canRead());
    Assert.assertTrue(new File(group, "modulea/1.0/modulea-1.0.jar").canRead());
    Assert.assertTrue(new File(group, "moduleb/1.0/moduleb-1.0.pom").canRead());
    Assert.assertTrue(new File(group, "moduleb/1.0/moduleb-1.0.jar").canRead());
  }

  @Test
  public void testBad() throws Exception {
    File basedir = resources.getBasedir("multimodule-deploy-at-end-bad");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    MavenExecutionResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .execute("deploy");

    result.assertLogText("[ERROR]");
    result.assertLogText("Basic.java:[10,24] <identifier> expected");
    result.assertLogText("Not performing deploy at end due to errors");

    File group = new File(remoterepo, "io/takari/lifecycle/its/multimodule-deploy-at-end-bad");
    Assert.assertFalse(new File(group, "parent/1.0/parent-1.0.pom").canRead());
    Assert.assertFalse(new File(group, "modulea/1.0/modulea-1.0.pom").canRead());
    Assert.assertFalse(new File(group, "modulea/1.0/modulea-1.0.jar").canRead());
    Assert.assertFalse(new File(group, "moduleb/1.0/moduleb-1.0.pom").canRead());
    Assert.assertFalse(new File(group, "moduleb/1.0/moduleb-1.0.jar").canRead());
  }
}
