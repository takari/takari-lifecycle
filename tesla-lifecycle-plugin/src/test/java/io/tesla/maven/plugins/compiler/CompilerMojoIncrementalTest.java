package io.tesla.maven.plugins.compiler;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.tesla.incremental.test.SpyBuildContextManager;
import org.junit.Assert;
import org.junit.Test;

public class CompilerMojoIncrementalTest extends AbstractCompileMojoTest {

  @Test
  public void testBasic() throws Exception {
    File basedir = getBasedir("src/test/projects/compile-incremental/basic");

    // initial build
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/basic/Basic1.class",
        "target/classes/basic/Basic2.class");
    assertBuildOutput(basedir, "src/main/java/basic/Basic1.java",
        "target/classes/basic/Basic1.class");
    assertBuildOutput(basedir, "src/main/java/basic/Basic2.java",
        "target/classes/basic/Basic2.class");

    // no-change rebuild
    SpyBuildContextManager.clear();
    compile(basedir);
    assertBuildOutputs(basedir, new String[0]);

    // one file changed
    SpyBuildContextManager.clear();
    cp(basedir, "src/main/java/basic/Basic1.java-changed", "src/main/java/basic/Basic1.java");
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/basic/Basic1.class");
    assertBuildOutput(basedir, "src/main/java/basic/Basic1.java",
        "target/classes/basic/Basic1.class");
  }

  @Test
  public void testBasic_timestampChangeRebuild() throws Exception {
    File basedir = getBasedir("src/test/projects/compile-incremental/basic");

    // initial build
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/basic/Basic1.class",
        "target/classes/basic/Basic2.class");

    // timestamp changed, assume output is regenerated with updated timestamp
    SpyBuildContextManager.clear();
    touch(basedir, "src/main/java/basic/Basic1.java");
    compile(basedir);
    // assertBuildOutputs( basedir, "target/classes/basic/Basic1.class" );

    assertBuildOutput(basedir, "src/main/java/basic/Basic1.java",
        "target/classes/basic/Basic1.class");
  }

  @Test
  public void testSupertype() throws Exception {
    File basedir = getBasedir("src/test/projects/compile-incremental/supertype");

    // initial build
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class",
        "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");

    // non-structural change
    SpyBuildContextManager.clear();
    cp(basedir, "src/main/java/supertype/SuperClass.java-comment",
        "src/main/java/supertype/SuperClass.java");
    cp(basedir, "src/main/java/supertype/SuperInterface.java-comment",
        "src/main/java/supertype/SuperInterface.java");
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");

    // superclass change
    SpyBuildContextManager.clear();
    cp(basedir, "src/main/java/supertype/SuperClass.java-member",
        "src/main/java/supertype/SuperClass.java");
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class",
        "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");
  }

  @Test
  public void testSupertype_superClassChangeDoesNotTriggerRebuildOfImplementedInterfaces()
      throws Exception {
    File basedir = getBasedir("src/test/projects/compile-incremental/supertype");

    // initial build
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class",
        "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");

    // superclass insignificant change
    SpyBuildContextManager.clear();
    cp(basedir, "src/main/java/supertype/SuperClass.java-methodBody",
        "src/main/java/supertype/SuperClass.java");
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/supertype/SuperClass.class");

    // superclass significant change
    SpyBuildContextManager.clear();
    cp(basedir, "src/main/java/supertype/SuperClass.java-member",
        "src/main/java/supertype/SuperClass.java");
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class",
        "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");
  }

  @Test
  public void testCirtular() throws Exception {
    File basedir = getCompiledBasedir("src/test/projects/compile-incremental/circular");
    assertBuildOutputs(basedir, "target/classes/circular/ClassA.class",
        "target/classes/circular/ClassB.class");
    SpyBuildContextManager.clear();

    touch(basedir, "src/main/java/circular/ClassA.java");
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/circular/ClassA.class",
        "target/classes/circular/ClassB.class");
  }

  @Test
  public void testRerence() throws Exception {
    File basedir = getCompiledBasedir("src/test/projects/compile-incremental/reference");
    assertBuildOutputs(basedir, "target/classes/reference/Parameter.class",
        "target/classes/reference/Type.class");

    // no change rebuild
    SpyBuildContextManager.clear();
    compile(basedir);
    assertBuildOutputs(basedir, new String[0]);

    // insignificant change
    SpyBuildContextManager.clear();
    cp(basedir, "src/main/java/reference/Parameter.java-comment",
        "src/main/java/reference/Parameter.java");
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/reference/Parameter.class");

    // significant change
    SpyBuildContextManager.clear();
    cp(basedir, "src/main/java/reference/Parameter.java-method",
        "src/main/java/reference/Parameter.java");
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/reference/Parameter.class",
        "target/classes/reference/Type.class");
  }

  @Test
  public void testMissing() throws Exception {
    File basedir = getBasedir("src/test/projects/compile-incremental/missing");
    try {
      compile(basedir);
    } catch (MojoExecutionException expected) {
      Assert.assertEquals("2 errors encountered, please see previous log/builds for more details",
          expected.getMessage());
    }
    assertBuildOutputs(basedir, "target/classes/missing/Other.class");
    Assert.assertFalse(new File(basedir, "target/classes/missing/Error.class").exists());

    Collection<String> messages =
        SpyBuildContextManager
            .getLogMessages(new File(basedir, "src/main/java/missing/Error.java"));
    Assert.assertEquals(toString(Arrays.asList("Missing cannot be resolved to a type",
        "Missing cannot be resolved to a type")), toString(messages));

    // no change rebuild
    SpyBuildContextManager.clear();
    try {
      compile(basedir);
    } catch (MojoExecutionException expected) {
      Assert.assertEquals("2 errors encountered, please see previous log/builds for more details",
          expected.getMessage());
    }
    assertBuildOutputs(basedir, new String[0]);

    messages =
        SpyBuildContextManager
            .getLogMessages(new File(basedir, "src/main/java/missing/Error.java"));
    Assert.assertEquals(toString(Arrays.asList("Missing cannot be resolved to a type",
        "Missing cannot be resolved to a type")), toString(messages));

    // fix the problem
    SpyBuildContextManager.clear();
    cp(basedir, "src/main/java/missing/Missing.java-missing", "src/main/java/missing/Missing.java");
    compile(basedir);
    assertBuildOutputs(basedir, "target/classes/missing/Error.class",
        "target/classes/missing/Missing.class");

    // reintroduce the problem
    SpyBuildContextManager.clear();
    rm(basedir, "src/main/java/missing/Missing.java");
    try {
      compile(basedir);
    } catch (MojoExecutionException expected) {
      Assert.assertEquals("2 errors encountered, please see previous log/builds for more details",
          expected.getMessage());
    }
    assertBuildOutputs(basedir, "target/classes/missing/Error.class",
        "target/classes/missing/Missing.class");
    Assert.assertFalse(new File(basedir, "target/classes/missing/Error.class").exists());
    Assert.assertFalse(new File(basedir, "target/classes/missing/Missing.class").exists());
  }

  @Test
  public void testMultifile() throws Exception {
    File basedir = getCompiledBasedir("src/test/projects/compile-incremental/multifile");
    assertBuildOutputs(basedir, "target/classes/multifile/ClassA.class",
        "target/classes/multifile/ClassB.class", "target/classes/multifile/ClassB$Nested.class");

    SpyBuildContextManager.clear();
    touch(basedir, "src/main/java/multifile/ClassA.java");
    cp(basedir, "src/main/java/multifile/ClassB.java-changed",
        "src/main/java/multifile/ClassB.java");
    try {
      compile(basedir);
    } catch (MojoExecutionException expected) {
      Assert.assertEquals("1 error encountered, please see previous log/builds for more details",
          expected.getMessage());
    }
    // TODO assert expected error messages
    assertBuildOutputs(basedir, "target/classes/multifile/ClassA.class",
        "target/classes/multifile/ClassB.class", "target/classes/multifile/ClassB$Nested.class");
    Assert.assertFalse(new File(basedir, "target/classes/multifile/ClassA.class").exists());
    Assert.assertTrue(new File(basedir, "target/classes/multifile/ClassB.class").exists());
    Assert.assertFalse(new File(basedir, "target/classes/multifile/ClassB$Nested.class").exists());
  }
}
