package io.takari.maven.plugins.compile.jdt;

import static io.takari.maven.testing.TestResources.cp;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;

public class CompileJdtClasspathTest extends AbstractCompileJdtTest {

  @Test
  public void testReactor() throws Exception {
    File parent = resources.getBasedir("compile-jdt-classpath/reactor-basic");

    compileReactor(parent);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");

    // no change rebuild
    compileReactor(parent);
    mojos.assertCarriedOverOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");

    // comment changes do NOT propagate
    cp(new File(parent, "module-b/src/main/java/reactor/moduleb"), "ModuleB.java-comment", "ModuleB.java");
    compileReactor(parent);
    mojos.assertCarriedOverOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");

    // API changes DO propagate
    cp(new File(parent, "module-b/src/main/java/reactor/moduleb"), "ModuleB.java-method", "ModuleB.java");
    compileReactor(parent);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");
  }

  @Test
  public void testReactor_missingType() throws Exception {
    File parent = resources.getBasedir("compile-jdt-classpath/reactor-missing");

    try {
      compileReactor(parent);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertBuildOutputs(parent, new String[0]);
    mojos.assertMessages(parent, "module-a/src/main/java/modulea/Error.java", "ERROR Error.java [3:8] The import moduleb cannot be resolved",
        "ERROR Error.java [8:12] Missing cannot be resolved to a type", "ERROR Error.java [10:20] Missing cannot be resolved to a type");

    // fix the problem and rebuild
    cp(parent, "module-b/src/main/java/moduleb/Missing.java-missing", "module-b/src/main/java/moduleb/Missing.java");
    compileReactor(parent);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/modulea/Error.class");
  }

  @Test
  public void testReactor_missingOnDemandImport() throws Exception {
    File parent = resources.getBasedir("compile-jdt-classpath/reactor-missing-ondemand-import");

    try {
      compileReactor(parent);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertBuildOutputs(parent, new String[0]);
    mojos.assertMessages(parent, "module-a/src/main/java/modulea/Error.java", "ERROR Error.java [3:8] The import moduleb cannot be resolved");

    // fix the problem and rebuild
    cp(parent, "module-b/src/main/java/moduleb/Missing.java-missing", "module-b/src/main/java/moduleb/Missing.java");
    compileReactor(parent);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/modulea/Error.class");
  }

  @Test
  public void testReactor_missingType_splitpackage() throws Exception {
    File parent = resources.getBasedir("compile-jdt-classpath/reactor-missing-splitpackage");

    try {
      compileReactor(parent);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertBuildOutputs(parent, new String[0]);
    mojos.assertMessages(parent, "module-a/src/main/java/missing/Error.java", "ERROR Error.java [6:12] Missing cannot be resolved to a type",
        "ERROR Error.java [8:20] Missing cannot be resolved to a type");

    // fix the problem and rebuild
    cp(parent, "module-b/src/main/java/missing/Missing.java-missing", "module-b/src/main/java/missing/Missing.java");
    compileReactor(parent);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/missing/Error.class");
  }

  @Test
  public void testRepository() throws Exception {
    File parent = resources.getBasedir("compile-jdt-classpath/repo-basic");

    MavenProject moduleA = mojos.readMavenProject(new File(parent, "module-a"));
    addDependency(moduleA, "module-b", new File(parent, "module-b/module-b.jar"));
    mojos.compile(moduleA);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");

    // dependency changed non-structurally
    moduleA = mojos.readMavenProject(new File(parent, "module-a"));
    addDependency(moduleA, "module-b-2", new File(parent, "module-b/module-b-comment.jar"));
    mojos.compile(moduleA);
    mojos.assertBuildOutputs(parent, new String[0]);

    // dependency changed structurally
    moduleA = mojos.readMavenProject(new File(parent, "module-a"));
    addDependency(moduleA, "module-b-2", new File(parent, "module-b/module-b-method.jar"));
    mojos.compile(moduleA);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");
  }

  @Test
  public void testRepository_classpathOrder() throws Exception {
    File parent = resources.getBasedir("compile-jdt-classpath/repo-basic");

    MavenProject moduleA = mojos.readMavenProject(new File(parent, "module-a"));
    addDependency(moduleA, "module-b", new File(parent, "module-b/module-b.jar"));
    addDependency(moduleA, "module-b-comment", new File(parent, "module-b/module-b-comment.jar"));
    addDependency(moduleA, "module-b-method", new File(parent, "module-b/module-b-method.jar"));
    mojos.compile(moduleA);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");

    // classpath order change did not result in structural type definition change
    moduleA = mojos.readMavenProject(new File(parent, "module-a"));
    addDependency(moduleA, "module-b-comment", new File(parent, "module-b/module-b-comment.jar"));
    addDependency(moduleA, "module-b", new File(parent, "module-b/module-b.jar"));
    addDependency(moduleA, "module-b-method", new File(parent, "module-b/module-b-method.jar"));
    mojos.compile(moduleA);
    mojos.assertBuildOutputs(parent, new String[0]);

    // classpath order change DID result in structural type definition change
    moduleA = mojos.readMavenProject(new File(parent, "module-a"));
    addDependency(moduleA, "module-b-method", new File(parent, "module-b/module-b-method.jar"));
    addDependency(moduleA, "module-b", new File(parent, "module-b/module-b.jar"));
    addDependency(moduleA, "module-b-comment", new File(parent, "module-b/module-b-comment.jar"));
    mojos.compile(moduleA);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");
  }

  private void compileReactor(File parent) throws Exception {
    mojos.flushClasspathCaches();

    File moduleB = new File(parent, "module-b");

    mojos.compile(moduleB);

    MavenProject moduleA = mojos.readMavenProject(new File(parent, "module-a"));
    addDependency(moduleA, "module-b", new File(moduleB, "target/classes"));

    mojos.compile(moduleA);
  }
}
