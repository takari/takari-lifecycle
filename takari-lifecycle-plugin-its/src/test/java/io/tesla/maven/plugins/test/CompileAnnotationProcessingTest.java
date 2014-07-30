package io.tesla.maven.plugins.test;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.it.VerifierResult;

import java.io.File;

import org.junit.Test;

public class CompileAnnotationProcessingTest extends AbstractIntegrationTest {

  public CompileAnnotationProcessingTest(String mavenVersion) throws Exception {
    super(mavenVersion);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("compile-proc");

    VerifierResult result = verifier.forProject(basedir).execute("package");
    result.assertErrorFreeLog();

    TestResources.assertFilesPresent(basedir, "project/target/classes/project/MyMyAnnotationClient.class");
  }
}
