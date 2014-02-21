package io.tesla.maven.plugins.compiler;

import static org.apache.maven.plugin.testing.resources.TestResources.cp;
import static org.apache.maven.plugin.testing.resources.TestResources.rm;
import static org.apache.maven.plugin.testing.resources.TestResources.touch;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;

public class CompileMojoIncrementalTest extends AbstractCompileMojoTest {

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("compile-incremental/basic");

    // initial build
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/basic/Basic1.class",
        "target/classes/basic/Basic2.class");
    assertBuildOutput(basedir, "src/main/java/basic/Basic1.java",
        "target/classes/basic/Basic1.class");
    assertBuildOutput(basedir, "src/main/java/basic/Basic2.java",
        "target/classes/basic/Basic2.class");

    // no-change rebuild
    compile(basedir);
    mojos.assertBuildOutputs(basedir, new String[0]);

    // one file changed
    cp(basedir, "src/main/java/basic/Basic1.java-changed", "src/main/java/basic/Basic1.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/basic/Basic1.class");
    assertBuildOutput(basedir, "src/main/java/basic/Basic1.java",
        "target/classes/basic/Basic1.class");
  }

  @Test
  public void testBasic_timestampChangeRebuild() throws Exception {
    File basedir = resources.getBasedir("compile-incremental/basic");

    // initial build
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/basic/Basic1.class",
        "target/classes/basic/Basic2.class");

    // timestamp changed, assume output is regenerated with updated timestamp
    touch(basedir, "src/main/java/basic/Basic1.java");
    compile(basedir);
    // assertBuildOutputs( basedir, "target/classes/basic/Basic1.class" );

    assertBuildOutput(basedir, "src/main/java/basic/Basic1.java",
        "target/classes/basic/Basic1.class");
  }

  @Test
  public void testSupertype() throws Exception {
    File basedir = resources.getBasedir("compile-incremental/supertype");

    // initial build
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class",
        "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");

    // non-structural change
    cp(basedir, "src/main/java/supertype/SuperClass.java-comment",
        "src/main/java/supertype/SuperClass.java");
    cp(basedir, "src/main/java/supertype/SuperInterface.java-comment",
        "src/main/java/supertype/SuperInterface.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");

    // superclass change
    cp(basedir, "src/main/java/supertype/SuperClass.java-member",
        "src/main/java/supertype/SuperClass.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class",
        "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");
  }

  @Test
  public void testSupertype_superClassChangeDoesNotTriggerRebuildOfImplementedInterfaces()
      throws Exception {
    File basedir = resources.getBasedir("compile-incremental/supertype");

    // initial build
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class",
        "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");

    // superclass insignificant change
    cp(basedir, "src/main/java/supertype/SuperClass.java-methodBody",
        "src/main/java/supertype/SuperClass.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SuperClass.class");

    // superclass significant change
    cp(basedir, "src/main/java/supertype/SuperClass.java-member",
        "src/main/java/supertype/SuperClass.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class",
        "target/classes/supertype/SuperClass.class",
        "target/classes/supertype/SuperInterface.class");
  }

  @Test
  public void testCirtular() throws Exception {
    File basedir = getCompiledBasedir("compile-incremental/circular");
    mojos.assertBuildOutputs(basedir, "target/classes/circular/ClassA.class",
        "target/classes/circular/ClassB.class");

    touch(basedir, "src/main/java/circular/ClassA.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/circular/ClassA.class",
        "target/classes/circular/ClassB.class");
  }

  @Test
  public void testRerence() throws Exception {
    File basedir = getCompiledBasedir("compile-incremental/reference");
    mojos.assertBuildOutputs(basedir, "target/classes/reference/Parameter.class",
        "target/classes/reference/Type.class");

    // no change rebuild
    compile(basedir);
    mojos.assertBuildOutputs(basedir, new String[0]);
    Assert.assertTrue(new File(basedir, "target/classes/reference/Parameter.class").canRead());
    Assert.assertTrue(new File(basedir, "target/classes/reference/Type.class").canRead());

    // insignificant change
    cp(basedir, "src/main/java/reference/Parameter.java-comment",
        "src/main/java/reference/Parameter.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/reference/Parameter.class");
    Assert.assertTrue(new File(basedir, "target/classes/reference/Type.class").canRead());

    // significant change
    cp(basedir, "src/main/java/reference/Parameter.java-method",
        "src/main/java/reference/Parameter.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/reference/Parameter.class",
        "target/classes/reference/Type.class");
  }

  @Test
  public void testMissing() throws Exception {
    final String[] messages =
        {"ERROR Error.java [6:12] Missing cannot be resolved to a type",
            "ERROR Error.java [8:20] Missing cannot be resolved to a type"};

    File basedir = resources.getBasedir("compile-incremental/missing");
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException expected) {
      Assert.assertEquals("2 error(s) encountered, see previous message(s) for details",
          expected.getMessage());
    }
    mojos.assertBuildOutputs(basedir, "target/classes/missing/Other.class");
    Assert.assertFalse(new File(basedir, "target/classes/missing/Error.class").exists());
    mojos.assertMessages(basedir, "src/main/java/missing/Error.java", messages);


    // no change rebuild
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException expected) {
      Assert.assertEquals("2 error(s) encountered, see previous message(s) for details",
          expected.getMessage());
    }
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos.assertMessages(basedir, "src/main/java/missing/Error.java", messages);

    // fix the problem
    cp(basedir, "src/main/java/missing/Missing.java-missing", "src/main/java/missing/Missing.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/missing/Error.class",
        "target/classes/missing/Missing.class");

    // reintroduce the problem
    rm(basedir, "src/main/java/missing/Missing.java");
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException expected) {
      Assert.assertEquals("2 error(s) encountered, see previous message(s) for details",
          expected.getMessage());
    }
    mojos.assertDeletedOutputs(basedir, "target/classes/missing/Error.class",
        "target/classes/missing/Missing.class");
  }

  @Test
  public void testMultifile() throws Exception {
    File basedir = getCompiledBasedir("compile-incremental/multifile");
    mojos.assertBuildOutputs(basedir, "target/classes/multifile/ClassA.class",
        "target/classes/multifile/ClassB.class", "target/classes/multifile/ClassB$Nested.class");

    touch(basedir, "src/main/java/multifile/ClassA.java");
    cp(basedir, "src/main/java/multifile/ClassB.java-changed",
        "src/main/java/multifile/ClassB.java");
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException expected) {
      Assert.assertEquals("1 error(s) encountered, see previous message(s) for details",
          expected.getMessage());
    }
    // TODO assert expected error messages
    mojos.assertBuildOutputs(basedir, "target/classes/multifile/ClassB.class");
    mojos.assertDeletedOutputs(basedir, "target/classes/multifile/ClassA.class",
        "target/classes/multifile/ClassB$Nested.class");
  }
}
