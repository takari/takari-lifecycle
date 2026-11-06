package io.takari.maven.plugins.compile;

import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.rm;
import static io.takari.maven.testing.TestResources.touch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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

        Path basedir = compile("compile-incremental/basic").toPath();
        Path classes = basedir.resolve("target/classes");
        mojos.assertBuildOutputs(classes.toFile(), "basic/Basic.class");

        // no-change rebuild
        compile(basedir.toFile());
        mojos.assertBuildOutputs(classes.toFile(), new String[0]);
        mojos.assertDeletedOutputs(classes.toFile(), new String[0]);
        mojos.assertCarriedOverOutputs(classes.toFile(), "basic/Basic.class");

        // change
        cp(basedir.toFile(), "src/main/java/basic/Basic.java-modified", "src/main/java/basic/Basic.java");
        compile(basedir.toFile());
        mojos.assertBuildOutputs(classes.toFile(), "basic/Basic.class");
    }

    @Test
    public void testBasic_identicalClassfile() throws Exception {
        Assume.assumeTrue("jdt".equals(compilerId)); // javac eagerly deletes and recreates all outputs

        Path basedir = compile("compile-incremental/basic").toPath();
        Path classes = basedir.resolve("target/classes");
        mojos.assertBuildOutputs(classes.toFile(), "basic/Basic.class");

        // move back timestamp, round to 10s to accommodate filesystem timestamp rounding
        long timestamp = System.currentTimeMillis() - 20000L;
        timestamp = timestamp - (timestamp % 10000L);
        Files.setLastModifiedTime(classes.resolve("basic/Basic.class"), FileTime.fromMillis(timestamp));

        cp(basedir.toFile(), "src/main/java/basic/Basic.java-comment", "src/main/java/basic/Basic.java");
        compile(basedir.toFile());
        mojos.assertBuildOutputs(classes.toFile(), "basic/Basic.class");
        Assert.assertEquals(
                timestamp,
                Files.getLastModifiedTime(classes.resolve("basic/Basic.class")).toMillis());
    }

    @Test
    public void testDelete() throws Exception {
        Path basedir = compile("compile-incremental/delete").toPath();
        mojos.assertBuildOutputs(
                basedir.resolve("target/classes").toFile(), "delete/Delete.class", "delete/Keep.class");

        Files.delete(basedir.resolve("src/main/java/delete/Delete.java"));
        compile(basedir.toFile());
        if ("jdt".equals(compilerId)) {
            mojos.assertCarriedOverOutputs(basedir.resolve("target/classes").toFile(), "delete/Keep.class");
        } else {
            mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "delete/Keep.class");
        }
        mojos.assertDeletedOutputs(basedir.resolve("target/classes").toFile(), "delete/Delete.class");
    }

    @Test
    public void testError() throws Exception {
        ErrorMessage expected = new ErrorMessage(compilerId);
        expected.setSnippets("jdt", "ERROR Error.java [4:11] Errorr cannot be resolved to a type");
        expected.setSnippets(
                "javac",
                "ERROR Error.java [4:11]",
                "cannot find symbol",
                "class Errorr",
                "location",
                "class error.Error");

        Path basedir = resources.getBasedir("compile-incremental/error").toPath();
        try {
            compile(basedir.toFile());
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertBuildOutputs(basedir.toFile(), new String[0]);
        mojos.assertMessage(basedir.toFile(), "src/main/java/error/Error.java", expected);

        // no change rebuild, should still fail with the same error
        try {
            compile(basedir.toFile());
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertBuildOutputs(basedir.toFile(), new String[0]);
        mojos.assertMessage(basedir.toFile(), "src/main/java/error/Error.java", expected);

        // fixed the error should clear the message during next build
        cp(basedir.toFile(), "src/main/java/error/Error.java-fixed", "src/main/java/error/Error.java");
        compile(basedir.toFile());
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/error/Error.class");
        mojos.assertMessages(basedir.toFile(), "target/classes/error/Error.class", new String[0]);
    }

    @Test
    public void testClasspath_reactor() throws Exception {
        Path basedir = resources.getBasedir("compile-incremental/classpath").toPath();
        Path moduleB = basedir.resolve("module-b");
        Path moduleA = basedir.resolve("module-a");

        compile(moduleB.toFile());
        MavenProject projectA = mojos.readMavenProject(moduleA.toFile());
        addDependency(projectA, "module-b", moduleB.resolve("target/classes").toFile());
        mojos.compile(projectA);
        mojos.assertBuildOutputs(moduleA.toFile(), "target/classes/modulea/ModuleA.class");

        // no change rebuild
        mojos.flushClasspathCaches();
        mojos.compile(projectA);
        mojos.assertBuildOutputs(moduleA.toFile(), new String[0]);

        // dependency changed "structurally"
        mojos.flushClasspathCaches();
        cp(moduleB.toFile(), "src/main/java/moduleb/ModuleB.java-method", "src/main/java/moduleb/ModuleB.java");
        touch(moduleB.toFile(), "src/main/java/moduleb/ModuleB.java");
        compile(moduleB.toFile());
        mojos.compile(projectA);
        mojos.assertBuildOutputs(moduleA.toFile(), "target/classes/modulea/ModuleA.class");
    }

    @Test
    public void testClasspath_dependency() throws Exception {
        Path basedir = resources.getBasedir("compile-incremental/classpath").toPath();
        Path moduleB = basedir.resolve("module-b");
        Path moduleA = basedir.resolve("module-a");

        MavenProject projectA = mojos.readMavenProject(moduleA.toFile());
        addDependency(projectA, "module-b", moduleB.resolve("module-b.jar").toFile());
        mojos.compile(projectA);
        mojos.assertBuildOutputs(moduleA.toFile(), "target/classes/modulea/ModuleA.class");

        // no change rebuild
        projectA = mojos.readMavenProject(moduleA.toFile());
        addDependency(projectA, "module-b", moduleB.resolve("module-b.jar").toFile());
        mojos.compile(projectA);
        mojos.assertBuildOutputs(moduleA.toFile(), new String[0]);

        // dependency changed "structurally"
        projectA = mojos.readMavenProject(moduleA.toFile());
        addDependency(
                projectA, "module-b", moduleB.resolve("module-b-method.jar").toFile());
        mojos.compile(projectA);
        mojos.assertBuildOutputs(moduleA.toFile(), "target/classes/modulea/ModuleA.class");
    }

    @Test
    public void testClasspath_changedClassMovedFromProjectToDependency() throws Exception {
        Path basedir = resources.getBasedir("compile-incremental/move").toPath();
        Path moduleB = basedir.resolve("module-b");
        Path moduleA = basedir.resolve("module-a");

        compile(moduleB.toFile());
        MavenProject projectA = mojos.readMavenProject(moduleA.toFile());
        addDependency(projectA, "module-b", moduleB.resolve("target/classes").toFile());
        mojos.compile(projectA);
        mojos.assertBuildOutputs(
                moduleA.toFile(), "target/classes/modulea/ModuleA.class", "target/classes/moving/Moving.class");

        mojos.flushClasspathCaches();

        // change and move class to the dependency
        rm(moduleA.toFile(), "src/main/java/moving/Moving.java");
        cp(moduleA.toFile(), "src/main/java/modulea/ModuleA.java-changed", "src/main/java/modulea/ModuleA.java");
        cp(moduleB.toFile(), "src/main/java/moving/Moving.java-changed", "src/main/java/moving/Moving.java");
        compile(moduleB.toFile());
        mojos.compile(projectA);
        mojos.assertDeletedOutputs(moduleA.toFile(), "target/classes/moving/Moving.class");
    }

    @Test
    public void testReferenceNested() throws Exception {
        Path basedir = compile("compile-incremental/reference-nested").toPath();
        Path classes = basedir.resolve("target/classes");
        mojos.assertBuildOutputs(classes.toFile(), "nested/A.class", "nested/Asecondary.class", "nested/B.class");

        cp(basedir.toFile(), "src/main/java/nested/A.java-changed", "src/main/java/nested/A.java");
        touch(basedir.toFile(), "src/main/java/nested/B.java");
        try {
            compile(basedir.toFile());
            Assert.fail();
        } catch (MojoExecutionException e) {
            // TODO check error message
        }
        Assert.assertFalse(Files.exists(classes.resolve("nested/B.class")));
    }

    @Test
    public void testCrossref() throws Exception {
        // two java files reference each other (via private static final members)
        // "structural" change in one file causes "structural" in the other and vice versa
        // assert incremental compiler does not go into endless loop

        // initial compile
        Path basedir = compile("compile-incremental/crossref").toPath();
        Path classes = basedir.resolve("target/classes");
        mojos.assertBuildOutputs(classes.toFile(), "crossref/A.class", "crossref/B.class");

        // change one of the files and incrementally recompile
        cp(basedir.toFile(), "src/main/java/crossref/A.java-modified", "src/main/java/crossref/A.java");
        compile(basedir.toFile());
        mojos.assertBuildOutputs(classes.toFile(), "crossref/A.class", "crossref/B.class");
    }
}
