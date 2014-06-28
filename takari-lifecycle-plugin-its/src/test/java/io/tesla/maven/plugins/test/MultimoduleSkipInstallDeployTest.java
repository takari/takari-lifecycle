package io.tesla.maven.plugins.test;

import java.io.File;

import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.Test;

public class MultimoduleSkipInstallDeployTest extends AbstractIntegrationTest {

  public MultimoduleSkipInstallDeployTest(String mavenVersion) {
    super(mavenVersion);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("multimodule-skip-install-deploy");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    Verifier verifier = getVerifier(basedir);
    verifier.addCliOption("-Drepopath=" + remoterepo.getCanonicalPath());
    verifier.executeGoal("deploy");
    verifier.verifyErrorFreeLog();

    File group = new File(remoterepo, "io/takari/lifecycle/its/multimodule-skip-install-deploy");
    Assert.assertTrue(new File(group, "parent/1.0/parent-1.0.pom").canRead());
    Assert.assertTrue(new File(group, "modulea/1.0/modulea-1.0.pom").canRead());
    Assert.assertTrue(new File(group, "modulea/1.0/modulea-1.0.jar").canRead());

    Assert.assertFalse(new File(group, "moduleb/1.0/modulea-1.0.pom").canRead());
    Assert.assertFalse(new File(group, "moduleb/1.0/modulea-1.0.jar").canRead());
  }

}
