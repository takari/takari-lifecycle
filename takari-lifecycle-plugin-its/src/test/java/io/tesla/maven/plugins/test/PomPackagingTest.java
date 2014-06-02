package io.tesla.maven.plugins.test;

import java.io.File;

import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.Test;

public class PomPackagingTest extends AbstractIntegrationTest {
  @Test
  public void testPomPackaging() throws Exception {
    File basedir = resources.getBasedir("pom-packaging");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    File localrepo = new File(getTestProperties().get("localRepository"));

    Verifier verifier = getVerifier(basedir);
    verifier.addCliOption("-Drepopath=" + remoterepo.getCanonicalPath());
    verifier.executeGoal("deploy");
    verifier.verifyErrorFreeLog();

    File localGroup = new File(localrepo, "io/takari/lifecycle/its/pom-packaging");
    Assert.assertTrue(new File(localGroup, "1.0/pom-packaging-1.0.pom").canRead());

    File remoteGroup = new File(remoterepo, "io/takari/lifecycle/its/pom-packaging");
    Assert.assertTrue(new File(remoteGroup, "1.0/pom-packaging-1.0.pom").canRead());
  }

}
