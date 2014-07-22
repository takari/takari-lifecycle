package io.takari.maven.plugins.compile;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

public class Java6InprocessAnnotationProcessingTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule();

  @Test
  public void testAnnotationProcessing_java6() throws Exception {
    Assume.assumeFalse(AbstractCompileTest.isJava7orBetter);

    // the point of this test is to validate in-process annotation processing fails fast on java6

    File basedir = resources.getBasedir("compile-proc/proc");

    Xpp3Dom proc = new Xpp3Dom("proc");
    proc.setValue("proc");

    try {
      mojos.compile(basedir, proc);
      Assert.fail();
    } catch (MojoExecutionException e) {
      Assert.assertEquals("Annotation processing requires forked JVM on Java 6", e.getMessage());
    }
  }
}
