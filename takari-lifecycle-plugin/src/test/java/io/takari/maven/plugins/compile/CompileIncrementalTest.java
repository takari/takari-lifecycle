package io.takari.maven.plugins.compile;

import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.rm;
import static io.takari.maven.testing.TestResources.touch;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class CompileIncrementalTest extends AbstractCompileTest {

  public CompileIncrementalTest(String compilerId) {
    super(compilerId);
  }

  @Test
  public void testBasic() throws Exception {

    File basedir = compile("compile-incremental/basic");
    File classes = new File(basedir, "target/classes");
    mojos.assertBuildOutputs(classes, "basic/Basic.class");

    // no-change rebuild
    compile(basedir);
    mojos.assertBuildOutputs(classes, new String[0]);
    mojos.assertDeletedOutputs(classes, new String[0]);
    mojos.assertCarriedOverOutputs(classes, "basic/Basic.class");

    // change
    cp(basedir, "src/main/java/basic/Basic.java-modified", "src/main/java/basic/Basic.java");
    compile(basedir);
    mojos.assertBuildOutputs(classes, "basic/Basic.class");
  }

  @Test
  public void testBasic_identicalClassfile() throws Exception {
    Assume.assumeTrue("jdt".equals(compilerId)); // javac eagerly deletes and recreates all outputs

    File basedir = compile("compile-incremental/basic");
    File classes = new File(basedir, "target/classes");
    mojos.assertBuildOutputs(classes, "basic/Basic.class");

    // move back timestamp, round to 10s to accommodate filesystem timestamp rounding
    long timestamp = System.currentTimeMillis() - 20000L;
    timestamp = timestamp - (timestamp % 10000L);
    new File(classes, "basic/Basic.class").setLastModified(timestamp);

    cp(basedir, "src/main/java/basic/Basic.java-comment", "src/main/java/basic/Basic.java");
    compile(basedir);
    mojos.assertBuildOutputs(classes, "basic/Basic.class");
    Assert.assertEquals(timestamp, new File(classes, "basic/Basic.class").lastModified());
  }

  @Test
  public void testDelete() throws Exception {
    File basedir = compile("compile-incremental/delete");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "delete/Delete.class", "delete/Keep.class");

    Assert.assertTrue(new File(basedir, "src/main/java/delete/Delete.java").delete());
    compile(basedir);
    if ("jdt".equals(compilerId)) {
      mojos.assertCarriedOverOutputs(new File(basedir, "target/classes"), "delete/Keep.class");
    } else {
      mojos.assertBuildOutputs(new File(basedir, "target/classes"), "delete/Keep.class");
    }
    mojos.assertDeletedOutputs(new File(basedir, "target/classes"), "delete/Delete.class");
  }

  @Test
  public void testError() throws Exception {
    ErrorMessage expected = new ErrorMessage(compilerId);
    expected.setSnippets("jdt", "ERROR Error.java [4:11] Errorr cannot be resolved to a type");
    expected.setSnippets("javac", "ERROR Error.java [4:11]", "cannot find symbol", "class Errorr", "location", "class error.Error");

    File basedir = resources.getBasedir("compile-incremental/error");
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos.assertMessage(basedir, "src/main/java/error/Error.java", expected);

    // no change rebuild, should still fail with the same error
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos.assertMessage(basedir, "src/main/java/error/Error.java", expected);

    // fixed the error should clear the message during next build
    cp(basedir, "src/main/java/error/Error.java-fixed", "src/main/java/error/Error.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/error/Error.class");
    mojos.assertMessages(basedir, "target/classes/error/Error.class", new String[0]);
  }

  @Test
  public void testClasspath_reactor() throws Exception {
    File basedir = resources.getBasedir("compile-incremental/classpath");
    File moduleB = new File(basedir, "module-b");
    File moduleA = new File(basedir, "module-a");

    compile(moduleB);
    MavenProject projectA = mojos.readMavenProject(moduleA);
    addDependency(projectA, "module-b", new File(moduleB, "target/classes"));
    mojos.compile(projectA);
    mojos.assertBuildOutputs(moduleA, "target/classes/modulea/ModuleA.class");

    // no change rebuild
    mojos.flushClasspathCaches();
    mojos.compile(projectA);
    mojos.assertBuildOutputs(moduleA, new String[0]);

    // dependency changed "structurally"
    mojos.flushClasspathCaches();
    cp(moduleB, "src/main/java/moduleb/ModuleB.java-method", "src/main/java/moduleb/ModuleB.java");
    touch(moduleB, "src/main/java/moduleb/ModuleB.java");
    compile(moduleB);
    mojos.compile(projectA);
    mojos.assertBuildOutputs(moduleA, "target/classes/modulea/ModuleA.class");
  }

  @Test
  public void testClasspath_dependency() throws Exception {
    File basedir = resources.getBasedir("compile-incremental/classpath");
    File moduleB = new File(basedir, "module-b");
    File moduleA = new File(basedir, "module-a");

    MavenProject projectA = mojos.readMavenProject(moduleA);
    addDependency(projectA, "module-b", new File(moduleB, "module-b.jar"));
    mojos.compile(projectA);
    mojos.assertBuildOutputs(moduleA, "target/classes/modulea/ModuleA.class");

    // no change rebuild
    projectA = mojos.readMavenProject(moduleA);
    addDependency(projectA, "module-b", new File(moduleB, "module-b.jar"));
    mojos.compile(projectA);
    mojos.assertBuildOutputs(moduleA, new String[0]);

    // dependency changed "structurally"
    projectA = mojos.readMavenProject(moduleA);
    addDependency(projectA, "module-b", new File(moduleB, "module-b-method.jar"));
    mojos.compile(projectA);
    mojos.assertBuildOutputs(moduleA, "target/classes/modulea/ModuleA.class");
  }

  @Test
  public void testClasspath_changedClassMovedFromProjectToDependency() throws Exception {
    File basedir = resources.getBasedir("compile-incremental/move");
    File moduleB = new File(basedir, "module-b");
    File moduleA = new File(basedir, "module-a");

    compile(moduleB);
    MavenProject projectA = mojos.readMavenProject(moduleA);
    addDependency(projectA, "module-b", new File(moduleB, "target/classes"));
    mojos.compile(projectA);
    mojos.assertBuildOutputs(moduleA, "target/classes/modulea/ModuleA.class", "target/classes/moving/Moving.class");

    mojos.flushClasspathCaches();

    // change and move class to the dependency
    rm(moduleA, "src/main/java/moving/Moving.java");
    cp(moduleA, "src/main/java/modulea/ModuleA.java-changed", "src/main/java/modulea/ModuleA.java");
    cp(moduleB, "src/main/java/moving/Moving.java-changed", "src/main/java/moving/Moving.java");
    compile(moduleB);
    mojos.compile(projectA);
    mojos.assertDeletedOutputs(moduleA, "target/classes/moving/Moving.class");
  }

  @Test
  public void testReferenceNested() throws Exception {
    File basedir = compile("compile-incremental/reference-nested");
    File classes = new File(basedir, "target/classes");
    mojos.assertBuildOutputs(classes, "nested/A.class", "nested/Asecondary.class", "nested/B.class");

    cp(basedir, "src/main/java/nested/A.java-changed", "src/main/java/nested/A.java");
    touch(basedir, "src/main/java/nested/B.java");
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // TODO check error message
    }
    Assert.assertFalse(new File(classes, "nested/B.class").exists());
  }

  @Test
  public void testCrossref() throws Exception {
    // two java files reference each other (via private static final members)
    // "structural" change in one file causes "structural" in the other and vice versa
    // assert incremental compiler does not go into endless loop

    // initial compile
    File basedir = compile("compile-incremental/crossref");
    File classes = new File(basedir, "target/classes");
    mojos.assertBuildOutputs(classes, "crossref/A.class", "crossref/B.class");

    // change one of the files and incrementally recompile
    cp(basedir, "src/main/java/crossref/A.java-modified", "src/main/java/crossref/A.java");
    compile(basedir);
    mojos.assertBuildOutputs(classes, "crossref/A.class", "crossref/B.class");
  }
}
