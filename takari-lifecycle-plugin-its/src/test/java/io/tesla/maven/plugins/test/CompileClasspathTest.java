package io.tesla.maven.plugins.test;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

import java.io.File;

import org.junit.Test;

public class CompileClasspathTest extends AbstractIntegrationTest {

  public CompileClasspathTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
    super(verifierBuilder);
  }

  @Test
  public void testClasspath() throws Exception {
    File basedir = resources.getBasedir("compile-classpath");

    MavenExecutionResult result = verifier.forProject(basedir).execute("compile");
    result.assertErrorFreeLog();
    result.assertLogText("takari-lifecycle-plugin:" + properties.getPluginVersion() + ":compile");
    // TODO assert the class file(s) were actually created
  }
}
