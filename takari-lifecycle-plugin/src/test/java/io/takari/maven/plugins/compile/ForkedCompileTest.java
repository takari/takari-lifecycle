package io.takari.maven.plugins.compile;

import io.takari.maven.testing.TestResources;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class ForkedCompileTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule();

  @Test
  public void testJvmMemoryOptions() throws Exception {
    File basedir = resources.getBasedir("compile/basic");

    Xpp3Dom fork = new Xpp3Dom("compilerId");
    fork.setValue("forked-javac");

    Xpp3Dom maxmem = new Xpp3Dom("maxmem");
    Xpp3Dom meminitial = new Xpp3Dom("meminitial");

    maxmem.setValue("64M");
    meminitial.setValue("64M");
    mojos.compile(basedir, fork, meminitial, maxmem);
    mojos.assertBuildOutputs(basedir, "target/classes/basic/Basic.class");

    // this is an awkward way to assert parameters worked
    // check if jvm startup fails with garbage -Xms/-Xmx parameters

    maxmem.setValue("garbage");
    meminitial.setValue("64M");
    try {
      mojos.compile(basedir, fork, meminitial, maxmem);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // TODO assert compilation failed for the right reason
    }

    maxmem.setValue("64M");
    meminitial.setValue("garbage");
    try {
      mojos.compile(basedir, fork, meminitial, maxmem);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // TODO assert compilation failed for the right reason
    }
  }
}
