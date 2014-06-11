package io.takari.maven.plugins.compile;

import static org.apache.maven.plugin.testing.resources.TestResources.cp;
import static org.apache.maven.plugin.testing.resources.TestResources.rm;
import static org.apache.maven.plugin.testing.resources.TestResources.touch;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

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
  public void testBasic_deletedOrModifiedClass() throws Exception {
    File basedir = compile("compile-incremental/basic");
    File classes = new File(basedir, "target/classes");
    mojos.assertBuildOutputs(classes, "basic/Basic.class");

    final File file = new File(classes, "basic/Basic.class");

    Assert.assertTrue(file.delete());
    compile(basedir);
    mojos.assertBuildOutputs(classes, "basic/Basic.class");

    Files.write("test", file, Charsets.UTF_8);
    compile(basedir);
    mojos.assertBuildOutputs(classes, "basic/Basic.class");
  }

  @Test
  public void testBasic_identicalClassfile() throws Exception {
    Assume.assumeFalse("Need to move IncrementalFileOutputStream to BuildContext", "jdt".equals(compilerId));

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
      Assert.assertEquals("1 error(s) encountered, see previous message(s) for details", e.getMessage());
    }
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos.assertMessage(basedir, "src/main/java/error/Error.java", expected);

    // no change rebuild, should still fail with the same error
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException e) {
      Assert.assertEquals("1 error(s) encountered, see previous message(s) for details", e.getMessage());
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
    mojos.compile(projectA);
    mojos.assertBuildOutputs(moduleA, new String[0]);

    // dependency changed "structurally"
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

    // change and move class to the dependency
    rm(moduleA, "src/main/java/moving/Moving.java");
    cp(moduleA, "src/main/java/modulea/ModuleA.java-changed", "src/main/java/modulea/ModuleA.java");
    cp(moduleB, "src/main/java/moving/Moving.java-changed", "src/main/java/moving/Moving.java");
    compile(moduleB);
    mojos.compile(projectA);
    mojos.assertDeletedOutputs(moduleA, "target/classes/moving/Moving.class");
  }
}
