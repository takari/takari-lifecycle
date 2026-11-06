package io.takari.maven.plugins.compile.jdt;

import static io.takari.maven.testing.TestResources.cp;

import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;

public class CompileJdtClasspathTest extends AbstractCompileJdtTest {

    @Test
    public void testReactor() throws Exception {
        Path parent =
                resources.getBasedir("compile-jdt-classpath/reactor-basic").toPath();

        compileReactor(parent);
        mojos.assertBuildOutputs(parent.toFile(), "module-a/target/classes/reactor/modulea/ModuleA.class");

        // no change rebuild
        compileReactor(parent);
        mojos.assertCarriedOverOutputs(parent.toFile(), "module-a/target/classes/reactor/modulea/ModuleA.class");

        // comment changes do NOT propagate
        cp(parent.resolve("module-b/src/main/java/reactor/moduleb").toFile(), "ModuleB.java-comment", "ModuleB.java");
        compileReactor(parent);
        mojos.assertCarriedOverOutputs(parent.toFile(), "module-a/target/classes/reactor/modulea/ModuleA.class");

        // API changes DO propagate
        cp(parent.resolve("module-b/src/main/java/reactor/moduleb").toFile(), "ModuleB.java-method", "ModuleB.java");
        compileReactor(parent);
        mojos.assertBuildOutputs(parent.toFile(), "module-a/target/classes/reactor/modulea/ModuleA.class");
    }

    @Test
    public void testReactor_missingType() throws Exception {
        Path parent =
                resources.getBasedir("compile-jdt-classpath/reactor-missing").toPath();

        try {
            compileReactor(parent);
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertBuildOutputs(parent.toFile(), new String[0]);
        mojos.assertMessages(
                parent.toFile(),
                "module-a/src/main/java/modulea/Error.java",
                "ERROR Error.java [3:8] The import moduleb cannot be resolved",
                "ERROR Error.java [8:12] Missing cannot be resolved to a type",
                "ERROR Error.java [10:20] Missing cannot be resolved to a type");

        // fix the problem and rebuild
        cp(
                parent.toFile(),
                "module-b/src/main/java/moduleb/Missing.java-missing",
                "module-b/src/main/java/moduleb/Missing.java");
        compileReactor(parent);
        mojos.assertBuildOutputs(parent.toFile(), "module-a/target/classes/modulea/Error.class");
    }

    @Test
    public void testReactor_missingOnDemandImport() throws Exception {
        Path parent = resources
                .getBasedir("compile-jdt-classpath/reactor-missing-ondemand-import")
                .toPath();

        try {
            compileReactor(parent);
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertBuildOutputs(parent.toFile(), new String[0]);
        mojos.assertMessages(
                parent.toFile(),
                "module-a/src/main/java/modulea/Error.java",
                "ERROR Error.java [3:8] The import moduleb cannot be resolved");

        // fix the problem and rebuild
        cp(
                parent.toFile(),
                "module-b/src/main/java/moduleb/Missing.java-missing",
                "module-b/src/main/java/moduleb/Missing.java");
        compileReactor(parent);
        mojos.assertBuildOutputs(parent.toFile(), "module-a/target/classes/modulea/Error.class");
    }

    @Test
    public void testReactor_missingType_splitpackage() throws Exception {
        Path parent = resources
                .getBasedir("compile-jdt-classpath/reactor-missing-splitpackage")
                .toPath();

        try {
            compileReactor(parent);
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertBuildOutputs(parent.toFile(), new String[0]);
        mojos.assertMessages(
                parent.toFile(),
                "module-a/src/main/java/missing/Error.java",
                "ERROR Error.java [6:12] Missing cannot be resolved to a type",
                "ERROR Error.java [8:20] Missing cannot be resolved to a type");

        // fix the problem and rebuild
        cp(
                parent.toFile(),
                "module-b/src/main/java/missing/Missing.java-missing",
                "module-b/src/main/java/missing/Missing.java");
        compileReactor(parent);
        mojos.assertBuildOutputs(parent.toFile(), "module-a/target/classes/missing/Error.class");
    }

    @Test
    public void testRepository() throws Exception {
        Path parent = resources.getBasedir("compile-jdt-classpath/repo-basic").toPath();

        MavenProject moduleA = mojos.readMavenProject(parent.resolve("module-a").toFile());
        addDependency(
                moduleA, "module-b", parent.resolve("module-b/module-b.jar").toFile());
        mojos.compile(moduleA);
        mojos.assertBuildOutputs(parent.toFile(), "module-a/target/classes/reactor/modulea/ModuleA.class");

        // dependency changed non-structurally
        moduleA = mojos.readMavenProject(parent.resolve("module-a").toFile());
        addDependency(
                moduleA,
                "module-b-2",
                parent.resolve("module-b/module-b-comment.jar").toFile());
        mojos.compile(moduleA);
        mojos.assertBuildOutputs(parent.toFile(), new String[0]);

        // dependency changed structurally
        moduleA = mojos.readMavenProject(parent.resolve("module-a").toFile());
        addDependency(
                moduleA,
                "module-b-2",
                parent.resolve("module-b/module-b-method.jar").toFile());
        mojos.compile(moduleA);
        mojos.assertBuildOutputs(parent.toFile(), "module-a/target/classes/reactor/modulea/ModuleA.class");
    }

    @Test
    public void testRepository_classpathOrder() throws Exception {
        Path parent = resources.getBasedir("compile-jdt-classpath/repo-basic").toPath();

        MavenProject moduleA = mojos.readMavenProject(parent.resolve("module-a").toFile());
        addDependency(
                moduleA, "module-b", parent.resolve("module-b/module-b.jar").toFile());
        addDependency(
                moduleA,
                "module-b-comment",
                parent.resolve("module-b/module-b-comment.jar").toFile());
        addDependency(
                moduleA,
                "module-b-method",
                parent.resolve("module-b/module-b-method.jar").toFile());
        mojos.compile(moduleA);
        mojos.assertBuildOutputs(parent.toFile(), "module-a/target/classes/reactor/modulea/ModuleA.class");

        // classpath order change did not result in structural type definition change
        moduleA = mojos.readMavenProject(parent.resolve("module-a").toFile());
        addDependency(
                moduleA,
                "module-b-comment",
                parent.resolve("module-b/module-b-comment.jar").toFile());
        addDependency(
                moduleA, "module-b", parent.resolve("module-b/module-b.jar").toFile());
        addDependency(
                moduleA,
                "module-b-method",
                parent.resolve("module-b/module-b-method.jar").toFile());
        mojos.compile(moduleA);
        mojos.assertBuildOutputs(parent.toFile(), new String[0]);

        // classpath order change DID result in structural type definition change
        moduleA = mojos.readMavenProject(parent.resolve("module-a").toFile());
        addDependency(
                moduleA,
                "module-b-method",
                parent.resolve("module-b/module-b-method.jar").toFile());
        addDependency(
                moduleA, "module-b", parent.resolve("module-b/module-b.jar").toFile());
        addDependency(
                moduleA,
                "module-b-comment",
                parent.resolve("module-b/module-b-comment.jar").toFile());
        mojos.compile(moduleA);
        mojos.assertBuildOutputs(parent.toFile(), "module-a/target/classes/reactor/modulea/ModuleA.class");
    }

    private void compileReactor(Path parent) throws Exception {
        mojos.flushClasspathCaches();

        Path moduleB = parent.resolve("module-b");

        mojos.compile(moduleB.toFile());

        MavenProject moduleA = mojos.readMavenProject(parent.resolve("module-a").toFile());
        addDependency(moduleA, "module-b", moduleB.resolve("target/classes").toFile());

        mojos.compile(moduleA);
    }
}
