package io.tesla.maven.plugins.test;

import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

import java.io.File;

import org.junit.Test;

public class CompileMultimoduleTest extends AbstractIntegrationTest {

  public CompileMultimoduleTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
    super(verifierBuilder);
  }

  @Test
  public void testCompile() throws Exception {
    File basedir = resources.getBasedir("compile-multimodule");

    verifier.forProject(basedir).execute("compile").assertErrorFreeLog();
  }

  @Test
  public void testTestCompile() throws Exception {
    File basedir = resources.getBasedir("compile-multimodule");

    verifier.forProject(basedir).execute("test-compile").assertErrorFreeLog();
  }
}
