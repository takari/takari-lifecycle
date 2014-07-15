package io.tesla.maven.plugins.test;

import io.takari.maven.testing.it.VerifierResult;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class PomPackagingTest extends AbstractIntegrationTest {
  public PomPackagingTest(String mavenVersion) throws Exception {
    super(mavenVersion);
  }

  @Test
  public void testPomPackaging() throws Exception {
    File basedir = resources.getBasedir("pom-packaging");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    File localrepo = properties.getLocalRepository();

    VerifierResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .execute("deploy");
    result.assertErrorFreeLog();

    File localGroup = new File(localrepo, "io/takari/lifecycle/its/pom-packaging");
    Assert.assertTrue(new File(localGroup, "1.0/pom-packaging-1.0.pom").canRead());

    File remoteGroup = new File(remoterepo, "io/takari/lifecycle/its/pom-packaging");
    Assert.assertTrue(new File(remoteGroup, "1.0/pom-packaging-1.0.pom").canRead());
  }

}
