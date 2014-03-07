package io.tesla.maven.plugins.compiler;

import io.takari.maven.plugins.compile.CompileRule;

import java.io.File;

import org.apache.maven.plugin.testing.resources.TestResources;
import org.junit.Assert;
import org.junit.Rule;

public abstract class AbstractCompileMojoTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule();

  protected void compile(File basedir) throws Exception {
    mojos.executeMojo(basedir, "compileXXX");
  }

  protected void testCompile(File basedir) throws Exception {
    mojos.executeMojo(basedir, "testCompileXXX");
  }

  protected File getCompiledBasedir(String location) throws Exception {
    final File basedir = resources.getBasedir(location);
    compile(basedir);
    return basedir;
  }

  /**
   * Asserts specified output exists and is not older than specified input
   */
  protected static void assertBuildOutput(File basedir, String input, String output) {
    File inputFile = new File(basedir, input);
    File outputFile = new File(basedir, output);
    Assert.assertTrue("output is older than input",
        outputFile.lastModified() >= inputFile.lastModified());
  }

}
