package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.io.File;

import org.apache.maven.plugin.testing.resources.TestResources;
import org.junit.Rule;
import org.junit.Test;

public class CompileTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("compile/basic");
    mojos.executeMojo(basedir, "compile-incremental");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }

  @Test
  public void testIncludes() throws Exception {
    File basedir = resources.getBasedir("compile/includes");
    mojos.executeMojo(basedir, "compile-incremental");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }

  @Test
  public void testExcludes() throws Exception {
    File basedir = resources.getBasedir("compile/excludes");
    mojos.executeMojo(basedir, "compile-incremental");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }
}
