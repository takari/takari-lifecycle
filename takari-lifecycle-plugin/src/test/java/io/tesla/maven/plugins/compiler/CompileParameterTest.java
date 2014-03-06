package io.tesla.maven.plugins.compiler;

import java.io.File;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

public class CompileParameterTest extends AbstractCompileMojoTest {
  @Test
  public void testJDKLevel() throws Exception {
    File basedir = getCompiledBasedir("compile-jdt-parameters/jdklevel");
    Collection<String> messages =
        mojos.getBuildContextLog().getMessages(
            new File(basedir, "src/main/java/jdklevel/ImplementsRunnable.java"));
    Assert.assertEquals(0, messages.size());
  }

  @Test
  public void testTestCompile() throws Exception {
    File basedir = resources.getBasedir("compile-jdt-parameters/testcompile");
    testCompile(basedir);
    mojos.assertBuildOutputs(basedir, "target/test-classes/testcompile/SomeTest.class");
  }
}
