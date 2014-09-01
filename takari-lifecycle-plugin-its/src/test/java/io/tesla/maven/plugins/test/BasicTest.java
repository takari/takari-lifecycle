package io.tesla.maven.plugins.test;

import io.takari.maven.testing.it.VerifierResult;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

public class BasicTest extends AbstractIntegrationTest {

  public BasicTest(File mavenInstallation, File classworldsConf, String version) throws Exception {
    super(mavenInstallation, classworldsConf, version);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("basic");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    File localrepo = properties.getLocalRepository();

    VerifierResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .execute("deploy");

    result.assertErrorFreeLog();

    // TODO assert expected mojos were executed
    // TODO assertFileExist, etc
    // TODO assert jar content
    Assert.assertTrue(new File(basedir, "target/basic-1.0.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-1.0-tests.jar").canRead());

    File localGroup = new File(localrepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(localGroup, "basic/1.0/basic-1.0.pom").canRead());
    Assert.assertTrue(new File(localGroup, "basic/1.0/basic-1.0.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic/1.0/basic-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic/1.0/basic-1.0-tests.jar").canRead());

    File remoteGroup = new File(remoterepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(remoteGroup, "basic/1.0/basic-1.0.pom").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic/1.0/basic-1.0.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic/1.0/basic-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic/1.0/basic-1.0-tests.jar").canRead());
  }

}
