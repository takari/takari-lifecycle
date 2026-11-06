package io.takari.maven.plugins.compile;

import static io.takari.maven.plugins.compile.ClassfileMatchers.hasAnnotation;
import static io.takari.maven.plugins.compile.ClassfileMatchers.hasDebugLines;
import static io.takari.maven.plugins.compile.ClassfileMatchers.hasDebugSource;
import static io.takari.maven.plugins.compile.ClassfileMatchers.hasDebugVars;
import static io.takari.maven.plugins.compile.ClassfileMatchers.hasMethodParameterWithName;
import static io.takari.maven.plugins.compile.ClassfileMatchers.isVersion;
import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.touch;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

import io.takari.maven.plugins.compile.javac.CompilerJavac;
import io.takari.maven.plugins.compile.jdt.CompilerJdt;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class CompileTest extends AbstractCompileTest {

    public CompileTest(String compilerId) {
        super(compilerId);
    }

    @Test
    public void testBasic() throws Exception {
        Path basedir = compile("compile/basic").toPath();
        File classes = basedir.resolve("target/classes").toFile();
        mojos.assertBuildOutputs(classes, "basic/Basic.class");
    }

    @Test
    public void testBasic_testCompile() throws Exception {
        Path basedir = compile("compile/basic").toPath();
        File testClasses = basedir.resolve("target/test-classes").toFile();

        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);
        MojoExecution execution = mojos.newMojoExecution("testCompile");
        execution.getConfiguration().addChild(newParameter("compilerId", compilerId));
        mojos.executeMojo(session, project, execution);

        mojos.assertBuildOutputs(testClasses, "basic/BasicTest.class");
    }

    @Test
    public void testBasic_verbose() throws Exception {
        PrintStream origOut = System.out;
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            System.setOut(new PrintStream(buf, true));
            Path basedir =
                    compile("compile/basic", newParameter("verbose", "true")).toPath();
            mojos.assertBuildOutputs(basedir.toFile(), "target/classes/basic/Basic.class");
            String output = new String(buf.toByteArray());
            Assert.assertTrue(output.contains("parsing "));
        } finally {
            System.setOut(origOut);
        }
    }

    @Test
    public void testBasic_debugInfo() throws Exception {
        Path basedir;

        // all
        basedir = compile("compile/basic").toPath();
        assertThat(
                basedir.resolve("target/classes/basic/Basic.class").toFile(),
                allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));
        basedir = compile("compile/basic", newParameter("debug", "all")).toPath();
        assertThat(
                basedir.resolve("target/classes/basic/Basic.class").toFile(),
                allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));
        basedir = compile("compile/basic", newParameter("debug", "true")).toPath();
        assertThat(
                basedir.resolve("target/classes/basic/Basic.class").toFile(),
                allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));

        // none
        basedir = compile("compile/basic", newParameter("debug", "none")).toPath();
        assertThat(
                basedir.resolve("target/classes/basic/Basic.class").toFile(),
                allOf(not(hasDebugSource()), not(hasDebugLines()), not(hasDebugVars())));
        basedir = compile("compile/basic", newParameter("debug", "false")).toPath();
        assertThat(
                basedir.resolve("target/classes/basic/Basic.class").toFile(),
                allOf(not(hasDebugSource()), not(hasDebugLines()), not(hasDebugVars())));

        // keywords
        basedir = compile("compile/basic", newParameter("debug", "source")).toPath();
        assertThat(
                basedir.resolve("target/classes/basic/Basic.class").toFile(),
                allOf(hasDebugSource(), not(hasDebugLines()), not(hasDebugVars())));
        basedir =
                compile("compile/basic", newParameter("debug", "source,lines")).toPath();
        assertThat(
                basedir.resolve("target/classes/basic/Basic.class").toFile(),
                allOf(hasDebugSource(), hasDebugLines(), not(hasDebugVars())));
        basedir = compile("compile/basic", newParameter("debug", "source,lines,vars"))
                .toPath();
        assertThat(
                basedir.resolve("target/classes/basic/Basic.class").toFile(),
                allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));
    }

    @Test
    public void testBasic_skipMain() throws Exception {
        Path basedir =
                compile("compile/basic", newParameter("skipMain", "true")).toPath();
        File classes = basedir.resolve("target/classes").toFile();
        mojos.assertBuildOutputs(classes, new String[0]);

        compile(basedir.toFile());
        mojos.assertBuildOutputs(classes, "basic/Basic.class");

        compile(basedir.toFile(), newParameter("skipMain", "true"));
        mojos.assertBuildOutputs(classes, new String[0]);
        mojos.assertDeletedOutputs(classes, new String[0]);
    }

    @Test
    public void testBasic_skip() throws Exception {
        Path basedir = compile("compile/basic").toPath();
        File testClasses = basedir.resolve("target/test-classes").toFile();

        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);
        MojoExecution execution = mojos.newMojoExecution("testCompile");
        execution.getConfiguration().addChild(newParameter("compilerId", compilerId));
        execution.getConfiguration().addChild(newParameter("skip", "true"));
        mojos.executeMojo(session, project, execution);
        mojos.assertBuildOutputs(testClasses, new String[0]);

        execution = mojos.newMojoExecution("testCompile");
        execution.getConfiguration().addChild(newParameter("compilerId", compilerId));
        mojos.executeMojo(session, project, execution);
        mojos.assertBuildOutputs(testClasses, "basic/BasicTest.class");

        execution = mojos.newMojoExecution("testCompile");
        execution.getConfiguration().addChild(newParameter("compilerId", compilerId));
        execution.getConfiguration().addChild(newParameter("skip", "true"));
        mojos.executeMojo(session, project, execution);
        mojos.assertBuildOutputs(testClasses, new String[0]);
        mojos.assertDeletedOutputs(testClasses, new String[0]);
    }

    @Test
    public void testBasic_java8() throws Exception {
        Path basedir = compile("compile/basic", newParameter("source", "1.8")).toPath();
        assertThat(basedir.resolve("target/classes/basic/Basic.class").toFile(), isVersion(52));
    }

    @Test
    public void testBasic_java9() throws Exception {
        assumeTrue(isJava9orBetter);
        Path basedir = compile("compile/basic", newParameter("source", "9")).toPath();
        assertThat(basedir.resolve("target/classes/basic/Basic.class").toFile(), isVersion(53));
    }

    @Test
    public void testBasic_java10() throws Exception {
        assumeTrue(isJava10orBetter);
        Path basedir = compile("compile/basic", newParameter("source", "10")).toPath();
        assertThat(basedir.resolve("target/classes/basic/Basic.class").toFile(), isVersion(54));
    }

    @Test
    public void testBasic_java11() throws Exception {
        assumeTrue(isJava10orBetter);
        Path basedir = compile("compile/basic", newParameter("source", "11")).toPath();
        assertThat(basedir.resolve("target/classes/basic/Basic.class").toFile(), isVersion(55));
    }

    @Test
    public void testIncludes() throws Exception {
        Xpp3Dom includes = new Xpp3Dom("includes");
        includes.addChild(newParameter("include", "basic/Basic.java"));
        Path basedir = compile("compile/source-filtering", includes).toPath();

        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "basic/Basic.class");
    }

    @Test
    public void testExcludes() throws Exception {
        Xpp3Dom excludes = new Xpp3Dom("excludes");
        excludes.addChild(newParameter("exclude", "basic/Garbage.java"));
        Path basedir = compile("compile/source-filtering", excludes).toPath();

        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "basic/Basic.class");
    }

    @Test
    public void testClasspath() throws Exception {
        File dependency =
                compile("compile/basic").toPath().resolve("target/classes").toFile();

        Path basedir = resources.getBasedir("compile/classpath").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);
        MojoExecution execution = mojos.newMojoExecution();

        addDependency(project, "dependency", dependency);

        mojos.executeMojo(session, project, execution);

        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "classpath/Classpath.class");
    }

    @Test
    public void testClasspath_dependencySourceTypes_ignore() throws Exception {
        // dependency has both .java and .class files, but .java file is corrupted
        // assert the compiler uses .class file when dependencySourceTypes=ignore

        Path dependency = compile("compile/basic").toPath();
        Files.writeString(dependency.resolve("target/classes/basic/Basic.java"), "corrupted", StandardCharsets.UTF_8);
        touch(dependency.resolve("target/classes/basic/Basic.java").toFile()); // javac will pick newer file by default

        Path basedir = resources.getBasedir("compile/classpath").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());

        addDependency(
                project, "dependency", dependency.resolve("target/classes").toFile());

        mojos.compile(project, newParameter("dependencySourceTypes", "ignore"));

        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "classpath/Classpath.class");
    }

    @Test
    public void testSpace() throws Exception {
        Path basedir = compile("compile/spa ce").toPath();
        Assert.assertTrue(basedir.toAbsolutePath().toString().contains(" "));
        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "space/Space.class");
    }

    @Test
    public void testError() throws Exception {
        ErrorMessage expected = new ErrorMessage(compilerId);
        expected.setSnippets("jdt", "ERROR Error.java [4:11] Foo cannot be resolved to a type");
        expected.setSnippets(
                "javac", "ERROR Error.java [4:11]", "cannot find symbol", "class Foo", "location", "class basic.Error");

        Path basedir = resources.getBasedir("compile/error").toPath();
        try {
            compile(basedir.toFile());
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertBuildOutputs(basedir.toFile(), new String[0]);
        mojos.assertMessage(basedir.toFile(), "src/main/java/error/Error.java", expected);
    }

    @Test
    public void testWarn() throws Exception {
        Path basedir = resources.getBasedir("compile/warn").toPath();
        compile(basedir.toFile()); // no warnings by default
        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "warn/Warn.class");
        mojos.assertMessages(basedir.toFile(), "src/main/java/warn/Warn.java", new String[0]);

        ErrorMessage expected = new ErrorMessage(compilerId);
        expected.setSnippets(
                "jdt",
                "WARNING Warn.java [5:3] List is a raw type. References to generic type List<E> should be parameterized");
        expected.setSnippets(
                "javac",
                "WARNING Warn.java [5:12] found raw type: java.util.List\n  missing type arguments for generic class java.util.List<E>");

        compile(basedir.toFile(), newParameter("showWarnings", "true"));
        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "warn/Warn.class");
        mojos.assertMessage(basedir.toFile(), "src/main/java/warn/Warn.java", expected);
    }

    @Test
    public void testEncoding() throws Exception {
        Path basedir = resources.getBasedir("compile/encoding").toPath();
        try {
            compile(basedir.toFile(), newParameter("encoding", "ISO-8859-5"));
            Assert.fail();
        } catch (MojoExecutionException e) {
            //
        }
        mojos.assertMessage(
                basedir.resolve("src/main/java/encoding/ISO8859p5.java").toFile(),
                "\u043f\u043e\u0440\u0443\u0441\u0441\u043a\u0438"); // "inrussian" in UTF8 Russian
    }

    @Test
    public void testParameters() throws Exception {
        Path basedir = resources.getBasedir("compile/parameters").toPath();
        compile(basedir.toFile(), newParameter("parameters", "true"), newParameter("source", "1.8"));
        assertThat(
                basedir.resolve("target/classes/parameters/MethodParameter.class")
                        .toFile(),
                hasMethodParameterWithName("myNamedParameter"));
    }

    @Test
    public void testEmpty() throws Exception {
        Path basedir = compile("compile/empty").toPath();
        mojos.assertBuildOutputs(basedir.toFile(), new String[0]);
        Assert.assertFalse("outputDirectory was not created", Files.exists(basedir.resolve("target/classes")));
    }

    @Test
    public void testIncludeNonJavaSources() throws Exception {
        Path basedir = resources.getBasedir("compile/unexpected-sources").toPath();
        try {
            compile(basedir.toFile());
        } catch (MojoExecutionException e) {
            Assert.assertEquals("<includes> patterns must end with .java. Illegal patterns: [**]", e.getMessage());
        }
        mojos.assertBuildOutputs(basedir.toFile(), new String[0]);
    }

    @Test
    public void testMultipleExecutions() throws Exception {
        Path basedir = resources.getBasedir("compile/multiple-executions").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);

        // compile "other" sources, execution id="other"
        MojoExecution execution = mojos.newMojoExecution();
        Xpp3Dom configuration = execution.getConfiguration();
        Xpp3Dom otherIncludes = new Xpp3Dom("includes");
        otherIncludes.addChild(newParameter("include", "other/*.java"));
        configuration.addChild(otherIncludes);
        execution = new MojoExecution(execution.getMojoDescriptor(), "other", execution.getSource());
        execution.setConfiguration(configuration);
        mojos.executeMojo(session, project, execution);
        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "other/Other.class");

        // compile main sources
        Xpp3Dom includes = new Xpp3Dom("includes");
        includes.addChild(newParameter("include", "main/*.java"));
        compile(basedir.toFile(), includes);
        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "main/Main.class");
    }

    @Test
    public void testBinaryTypeMessage() throws Exception {
        // javac (tested with 1.8.0_05 and 1.7.0_45) produce warning messages for dependency .class files in some cases
        // the point of this test is to verify compile mojo tolerates this messages, i.e. does not fail
        // in this particular test, the message is triggered by missing @annotation referenced from a dependency class

        Path basedir = resources.getBasedir("compile/binary-class-message").toPath();

        MavenProject project = mojos.readMavenProject(basedir.resolve("project").toFile());
        MavenSession session = mojos.newMavenSession(project);
        MojoExecution execution = mojos.newMojoExecution();
        addDependency(project, "dependency", basedir.resolve("annotated.zip").toFile());
        mojos.executeMojo(session, project, execution);
        mojos.assertBuildOutputs(basedir.resolve("project/target/classes").toFile(), "project/Project.class");
    }

    @Test
    public void testImplicitClassfileGeneration() throws Exception {
        // javac automatically generates class files from sources found on classpath in some cases
        // the point of this test is to make sure this behaviour is disabled

        Path dependency = compile("compile/basic").toPath();
        cp(dependency.toFile(), "src/main/java/basic/Basic.java", "target/classes/basic/Basic.java");
        touch(dependency.toFile(), "target/classes/basic/Basic.java"); // must be newer than .class file

        Path basedir = resources.getBasedir("compile/implicit-classfile").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);
        MojoExecution execution = mojos.newMojoExecution();

        addDependency(
                project, "dependency", dependency.resolve("target/classes").toFile());

        mojos.executeMojo(session, project, execution);

        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "implicit/Implicit.class");
    }

    @Test
    public void testAnnotation() throws Exception {
        Path basedir = compile("compile/annotation").toPath();

        assertThat(
                basedir.resolve("target/classes/annotation/AnnotatedClass.class")
                        .toFile(),
                hasAnnotation("annotation.Annotation"));
    }

    @Test
    @Ignore("The test is useful but needs to be reimplemented")
    public void testInnerTypeDependency_sourceDependencies() throws Exception {
        Path basedir = resources.getBasedir("compile/inner-type-dependency").toPath();

        MavenProject project = mojos.readMavenProject(basedir.toFile());
        addDependency(
                project,
                "dependency",
                basedir.resolve("dependency/src/main/java").toFile());

        mojos.compile(project, newParameter("dependencySourceTypes", "prefer"));
        mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "innertyperef/InnerTypeRef.class");
    }

    @Test
    public void testFailOnErrorTrue() throws Exception {
        ErrorMessage expected = new ErrorMessage(compilerId);
        String msg;
        if (compilerId.equals(CompilerJdt.ID)) {
            msg = "ERROR Error.java [5:13] Strin cannot be resolved to a type";
            expected.setSnippets(CompilerJdt.ID, msg);
        } else {
            // javac and forked-javac
            msg = "ERROR Error.java [5:13] cannot find symbol\n" + //
                    "  symbol:   class Strin\n"
                    + //
                    "  location: class basic.Error";
            expected.setSnippets(CompilerJavac.ID, msg);
        }

        Path basedir = resources.getBasedir("compile/failOnError").toPath();
        try {
            compile("compile/failOnError");
            // Assert.fail();
        } catch (MojoExecutionException e) {
            //
        }
        mojos.assertMessage(basedir.toFile(), "src/main/java/basic/Error.java", expected);
    }

    @Test
    public void testFailOnErrorFalse() throws Exception {
        try {
            Path basedir = resources.getBasedir("compile/failOnError").toPath();
            compile("compile/failOnError", newParameter("failOnError", "false"));
            if (compilerId.equals(CompilerJdt.ID)) {
                mojos.assertBuildOutputs(basedir.resolve("target/classes").toFile(), "basic/Basic.class");
            }
        } catch (MojoExecutionException e) {
            Assert.fail();
        }
    }
}
