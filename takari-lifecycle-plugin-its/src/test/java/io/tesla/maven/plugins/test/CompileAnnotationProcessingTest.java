package io.tesla.maven.plugins.test;

import java.io.File;

import org.apache.maven.it.Verifier;
import org.junit.Test;

public class CompileAnnotationProcessingTest extends AbstractIntegrationTest {

  public CompileAnnotationProcessingTest(String mavenVersion) {
    super(mavenVersion);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("compile-proc");

    Verifier verifier = getVerifier(basedir);
    verifier.executeGoal("package");
    verifier.verifyErrorFreeLog();

    verifier.assertFilePresent("project/target/classes/project/MyMyAnnotationClient.class");
  }
}
