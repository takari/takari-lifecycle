package io.takari.maven.plugins.compile.jdt;

import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.rm;
import static io.takari.maven.testing.TestResources.touch;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;

public class CompileJdtTest extends AbstractCompileJdtTest {

  /**
   * Asserts specified output exists and is not older than specified input
   */
  protected static void assertBuildOutput(File basedir, String input, String output) {
    File inputFile = new File(basedir, input);
    File outputFile = new File(basedir, output);
    Assert.assertTrue("output is older than input", outputFile.lastModified() >= inputFile.lastModified());
  }


  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("compile-jdt/basic");

    // initial build
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/basic/Basic1.class", "target/classes/basic/Basic2.class");
    assertBuildOutput(basedir, "src/main/java/basic/Basic1.java", "target/classes/basic/Basic1.class");
    assertBuildOutput(basedir, "src/main/java/basic/Basic2.java", "target/classes/basic/Basic2.class");

    // no-change rebuild
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos.assertCarriedOverOutputs(basedir, "target/classes/basic/Basic1.class", "target/classes/basic/Basic2.class");

    // one file changed
    cp(basedir, "src/main/java/basic/Basic1.java-changed", "src/main/java/basic/Basic1.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/basic/Basic1.class");
    assertBuildOutput(basedir, "src/main/java/basic/Basic1.java", "target/classes/basic/Basic1.class");
  }

  @Test
  public void testBasic_timestampChangeRebuild() throws Exception {
    File basedir = resources.getBasedir("compile-jdt/basic");
    File classes = new File(basedir, "target/classes");

    // initial build
    mojos.compile(basedir);
    mojos.assertBuildOutputs(classes, "basic/Basic1.class", "basic/Basic2.class");

    // move back timestamp, round to 10s to accommodate filesystem timestamp rounding
    long timestamp = System.currentTimeMillis() - 20000L;
    timestamp = timestamp - (timestamp % 10000L);
    new File(classes, "basic/Basic1.class").setLastModified(timestamp);

    touch(basedir, "src/main/java/basic/Basic1.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(classes, "basic/Basic1.class");
    // timestamp is the same if the file is the same
    Assert.assertEquals(timestamp, new File(classes, "basic/Basic1.class").lastModified());
  }

  @Test
  public void testSupertype() throws Exception {
    File basedir = resources.getBasedir("compile-jdt/supertype");

    // initial build
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class", "target/classes/supertype/SuperClass.class", "target/classes/supertype/SuperInterface.class");

    // non-structural change
    cp(basedir, "src/main/java/supertype/SuperClass.java-comment", "src/main/java/supertype/SuperClass.java");
    cp(basedir, "src/main/java/supertype/SuperInterface.java-comment", "src/main/java/supertype/SuperInterface.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SuperClass.class", "target/classes/supertype/SuperInterface.class");

    // superclass change
    cp(basedir, "src/main/java/supertype/SuperClass.java-member", "src/main/java/supertype/SuperClass.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class", "target/classes/supertype/SuperClass.class");
  }

  @Test
  public void testSupertype_superClassChangeDoesNotTriggerRebuildOfImplementedInterfaces() throws Exception {
    File basedir = resources.getBasedir("compile-jdt/supertype");

    // initial build
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class", "target/classes/supertype/SuperClass.class", "target/classes/supertype/SuperInterface.class");

    // superclass insignificant change
    cp(basedir, "src/main/java/supertype/SuperClass.java-methodBody", "src/main/java/supertype/SuperClass.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SuperClass.class");

    // superclass significant change
    cp(basedir, "src/main/java/supertype/SuperClass.java-member", "src/main/java/supertype/SuperClass.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/supertype/SubClass.class", "target/classes/supertype/SuperClass.class");
  }

  @Test
  public void testCircular() throws Exception {
    File basedir = mojos.compile(resources.getBasedir("compile-jdt/circular"));
    mojos.assertBuildOutputs(basedir, "target/classes/circular/ClassA.class", "target/classes/circular/ClassB.class");

    cp(basedir, "src/main/java/circular/ClassA.java-changed", "src/main/java/circular/ClassA.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/circular/ClassA.class", "target/classes/circular/ClassB.class");
  }

  @Test
  public void testReference() throws Exception {
    File basedir = mojos.compile(resources.getBasedir("compile-jdt/reference"));
    mojos.assertBuildOutputs(basedir, "target/classes/reference/Parameter.class", "target/classes/reference/Type.class");

    // no change rebuild
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, new String[0]);
    Assert.assertTrue(new File(basedir, "target/classes/reference/Parameter.class").canRead());
    Assert.assertTrue(new File(basedir, "target/classes/reference/Type.class").canRead());

    // insignificant change
    cp(basedir, "src/main/java/reference/Parameter.java-comment", "src/main/java/reference/Parameter.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/reference/Parameter.class");
    Assert.assertTrue(new File(basedir, "target/classes/reference/Type.class").canRead());

    // significant change
    cp(basedir, "src/main/java/reference/Parameter.java-method", "src/main/java/reference/Parameter.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/reference/Parameter.class", "target/classes/reference/Type.class");
  }

  @Test
  public void testMissing() throws Exception {
    final String[] messages = {"ERROR Error.java [6:12] Missing cannot be resolved to a type", "ERROR Error.java [8:20] Missing cannot be resolved to a type"};

    File basedir = resources.getBasedir("compile-jdt/missing");
    try {
      mojos.compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException expected) {
      // expected
    }
    mojos.assertBuildOutputs(basedir, "target/classes/missing/Other.class");
    Assert.assertFalse(new File(basedir, "target/classes/missing/Error.class").exists());
    mojos.assertMessages(basedir, "src/main/java/missing/Error.java", messages);


    // no change rebuild
    try {
      mojos.compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException expected) {
      // expected
    }
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos.assertMessages(basedir, "src/main/java/missing/Error.java", messages);

    // fix the problem
    cp(basedir, "src/main/java/missing/Missing.java-missing", "src/main/java/missing/Missing.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/missing/Error.class", "target/classes/missing/Missing.class");

    // reintroduce the problem
    rm(basedir, "src/main/java/missing/Missing.java");
    try {
      mojos.compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException expected) {
      // expected
    }
    mojos.assertDeletedOutputs(basedir, "target/classes/missing/Error.class", "target/classes/missing/Missing.class");
  }

  @Test
  public void testMultifile() throws Exception {
    File basedir = mojos.compile(resources.getBasedir("compile-jdt/multifile"));
    mojos.assertBuildOutputs(basedir, "target/classes/multifile/ClassA.class", "target/classes/multifile/ClassB.class", "target/classes/multifile/ClassB$Nested.class");

    touch(basedir, "src/main/java/multifile/ClassA.java");
    cp(basedir, "src/main/java/multifile/ClassB.java-changed", "src/main/java/multifile/ClassB.java");
    try {
      mojos.compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException expected) {
      // expected
    }
    // TODO assert expected error messages
    mojos.assertBuildOutputs(basedir, "target/classes/multifile/ClassB.class");
    mojos.assertDeletedOutputs(basedir, "target/classes/multifile/ClassA.class", "target/classes/multifile/ClassB$Nested.class");
  }

  @Test
  public void testSecondaryType() throws Exception {
    File basedir = mojos.compile(resources.getBasedir("compile-jdt/secondary-type"));
    File classes = new File(basedir, "target/classes");
    mojos.assertBuildOutputs(classes //
        , "secondary/Primary.class" //
        , "secondary/Secondary.class" //
        , "secondary/SecondarySubclass.class" //
        , "secondary/SecondarySubclassClient.class");

    touch(basedir, "src/main/java/secondary/SecondarySubclassClient.java");
    mojos.compile(basedir);
    mojos.assertBuildOutputs(classes //
        , "secondary/SecondarySubclassClient.class");
  }

  @Test
  public void testRemovedTypes() throws Exception {
    File basedir = mojos.compile(resources.getBasedir("compile-jdt/removed-types"));
    File classes = new File(basedir, "target/classes");
    mojos.assertBuildOutputs(classes //
        , "removed/A.class" //
        , "removed/B.class" //
        , "removed/Dummy.class");

    rm(basedir, "src/main/java/removed/A.java");
    rm(basedir, "src/main/java/removed/B.java");
    mojos.compile(basedir);
    mojos.assertDeletedOutputs(classes //
        , "removed/A.class" //
        , "removed/B.class");
  }

  @Test
  public void testRemoveSecondaryType() throws Exception {
    File basedir = mojos.compile(resources.getBasedir("compile-jdt/remove-secondary-type"));
    File classes = new File(basedir, "target/classes");
    mojos.assertBuildOutputs(classes //
        , "secondary/A.class" //
        , "secondary/B.class" //
        , "secondary/ASecondary.class");

    cp(basedir, "src/main/java/secondary/A.java-changed", "src/main/java/secondary/A.java");
    try {
      mojos.compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException expected) {
      // TODO assert error message
    }
    mojos.assertBuildOutputs(classes //
        , "secondary/A.class");
    mojos.assertDeletedOutputs(classes //
        , "secondary/B.class" //
        , "secondary/ASecondary.class");
  }
}
