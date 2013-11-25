package io.tesla.maven.plugins.compiler;

import java.io.File;
import java.util.Collection;

import org.eclipse.tesla.incremental.test.SpyBuildContextManager;
import org.junit.Assert;
import org.junit.Test;

public class CompileMojoParameterTest extends AbstractCompileMojoTest {
  @Test
  public void testJDKLevel() throws Exception {
    File basedir = getCompiledBasedir("src/test/projects/compile-parameters/jdklevel");
    Collection<String> messages =
        SpyBuildContextManager.getLogMessages(new File(basedir,
            "src/main/java/jdklevel/ImplementsRunnable.java"));
    Assert.assertEquals(0, messages.size());
  }

  @Test
  public void testTestCompile() throws Exception {
    File basedir = getBasedir("src/test/projects/compile-parameters/testcompile");
    testCompile(basedir);
    assertBuildOutputs(basedir, "target/test-classes/testcompile/SomeTest.class");
  }
}
