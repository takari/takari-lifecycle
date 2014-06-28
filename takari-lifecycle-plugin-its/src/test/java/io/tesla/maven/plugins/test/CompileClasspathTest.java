package io.tesla.maven.plugins.test;

import java.io.File;

import org.apache.maven.it.Verifier;
import org.junit.Test;

public class CompileClasspathTest extends AbstractIntegrationTest {

  public CompileClasspathTest(String mavenVersion) {
    super(mavenVersion);
  }

  @Test
  public void testClasspath() throws Exception {
    File basedir = resources.getBasedir("compile-classpath");
    Verifier verifier = getVerifier(basedir);
    verifier.executeGoal("compile");
    verifier.verifyErrorFreeLog();

    verifier.verifyTextInLog("takari-lifecycle-plugin:" + getPluginVersion() + ":compile");
    // TODO assert the class file(s) were actually created
  }
}
