package io.tesla.maven.plugins.test;

import java.io.File;

import org.apache.maven.it.Verifier;
import org.junit.Assert;
import org.junit.Test;

public class BasicTest extends AbstractIntegrationTest {

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("basic");
    Verifier verifier = getVerifier(basedir);
    verifier.executeGoal("install"); // TODO deploy
    verifier.verifyErrorFreeLog();

    // TODO assert expected mojos were executed
    // TODO assertFileExist, etc
    // TODO assert jar content
    Assert.assertTrue(new File(basedir, "target/basic-1.0.0-SNAPSHOT.jar").canRead());
  }

}
