package io.tesla.maven.plugins.test;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

import java.io.File;

import org.junit.Test;

public class CompileAnnotationProcessingTest extends AbstractIntegrationTest {

  public CompileAnnotationProcessingTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
    super(verifierBuilder);
  }

  private void test(String compilerId) throws Exception {
    File basedir = resources.getBasedir("compile-proc");

    MavenExecutionResult result = verifier.forProject(basedir).withCliOption("-DcompilerId=" + compilerId).execute("package");
    result.assertErrorFreeLog();

    TestResources.assertFilesPresent(basedir, "project/target/classes/project/MyMyAnnotationClient.class");
  }

  @Test
  public void testBasicJdt() throws Exception {
    test("jdt");
  }

  @Test
  public void testBasicJavac() throws Exception {
    test("javac");
  }


}
