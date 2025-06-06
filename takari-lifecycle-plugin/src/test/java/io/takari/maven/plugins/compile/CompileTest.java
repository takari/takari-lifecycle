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
        File basedir = compile("compile/basic");
        File classes = new File(basedir, "target/classes");
        mojos.assertBuildOutputs(classes, "basic/Basic.class");
    }

    @Test
    public void testBasic_testCompile() throws Exception {
        File basedir = compile("compile/basic");
        File testClasses = new File(basedir, "target/test-classes");

        MavenProject project = mojos.readMavenProject(basedir);
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
            File basedir = compile("compile/basic", newParameter("verbose", "true"));
            mojos.assertBuildOutputs(basedir, "target/classes/basic/Basic.class");
            String output = new String(buf.toByteArray());
            Assert.assertTrue(output.contains("parsing "));
        } finally {
            System.setOut(origOut);
        }
    }

    @Test
    public void testBasic_debugInfo() throws Exception {
        File basedir;

        // all
        basedir = compile("compile/basic");
        assertThat(
                new File(basedir, "target/classes/basic/Basic.class"),
                allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));
        basedir = compile("compile/basic", newParameter("debug", "all"));
        assertThat(
                new File(basedir, "target/classes/basic/Basic.class"),
                allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));
        basedir = compile("compile/basic", newParameter("debug", "true"));
        assertThat(
                new File(basedir, "target/classes/basic/Basic.class"),
                allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));

        // none
        basedir = compile("compile/basic", newParameter("debug", "none"));
        assertThat(
                new File(basedir, "target/classes/basic/Basic.class"),
                allOf(not(hasDebugSource()), not(hasDebugLines()), not(hasDebugVars())));
        basedir = compile("compile/basic", newParameter("debug", "false"));
        assertThat(
                new File(basedir, "target/classes/basic/Basic.class"),
                allOf(not(hasDebugSource()), not(hasDebugLines()), not(hasDebugVars())));

        // keywords
        basedir = compile("compile/basic", newParameter("debug", "source"));
        assertThat(
                new File(basedir, "target/classes/basic/Basic.class"),
                allOf(hasDebugSource(), not(hasDebugLines()), not(hasDebugVars())));
        basedir = compile("compile/basic", newParameter("debug", "source,lines"));
        assertThat(
                new File(basedir, "target/classes/basic/Basic.class"),
                allOf(hasDebugSource(), hasDebugLines(), not(hasDebugVars())));
        basedir = compile("compile/basic", newParameter("debug", "source,lines,vars"));
        assertThat(
                new File(basedir, "target/classes/basic/Basic.class"),
                allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));
    }

    @Test
    public void testBasic_skipMain() throws Exception {
        File basedir = compile("compile/basic", newParameter("skipMain", "true"));
        File classes = new File(basedir, "target/classes");
        mojos.assertBuildOutputs(classes, new String[0]);

        compile(basedir);
        mojos.assertBuildOutputs(classes, "basic/Basic.class");

        compile(basedir, newParameter("skipMain", "true"));
        mojos.assertBuildOutputs(classes, new String[0]);
        mojos.assertDeletedOutputs(classes, new String[0]);
    }

    @Test
    public void testBasic_skip() throws Exception {
        File basedir = compile("compile/basic");
        File testClasses = new File(basedir, "target/test-classes");

        MavenProject project = mojos.readMavenProject(basedir);
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
    @Ignore("Java 7 source/target is not supported on recent JDKs")
    public void testBasic_java7() throws Exception {
        File basedir = compile("compile/basic", newParameter("source", "1.7"));
        assertThat(new File(basedir, "target/classes/basic/Basic.class"), isVersion(51));
    }

    @Test
    public void testBasic_java8() throws Exception {
        File basedir = compile("compile/basic", newParameter("source", "1.8"));
        assertThat(new File(basedir, "target/classes/basic/Basic.class"), isVersion(52));
    }

    @Test
    public void testBasic_java9() throws Exception {
        assumeTrue(isJava9orBetter);
        File basedir = compile("compile/basic", newParameter("source", "9"));
        assertThat(new File(basedir, "target/classes/basic/Basic.class"), isVersion(53));
    }

    @Test
    public void testBasic_java10() throws Exception {
        assumeTrue(isJava10orBetter);
        File basedir = compile("compile/basic", newParameter("source", "10"));
        assertThat(new File(basedir, "target/classes/basic/Basic.class"), isVersion(54));
    }

    @Test
    public void testBasic_java11() throws Exception {
        assumeTrue(isJava10orBetter);
        File basedir = compile("compile/basic", newParameter("source", "11"));
        assertThat(new File(basedir, "target/classes/basic/Basic.class"), isVersion(55));
    }

    @Test
    public void testIncludes() throws Exception {
        Xpp3Dom includes = new Xpp3Dom("includes");
        includes.addChild(newParameter("include", "basic/Basic.java"));
        File basedir = compile("compile/source-filtering", includes);

        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
    }

    @Test
    public void testExcludes() throws Exception {
        Xpp3Dom excludes = new Xpp3Dom("excludes");
        excludes.addChild(newParameter("exclude", "basic/Garbage.java"));
        File basedir = compile("compile/source-filtering", excludes);

        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
    }

    @Test
    public void testClasspath() throws Exception {
        File dependency = new File(compile("compile/basic"), "target/classes");

        File basedir = resources.getBasedir("compile/classpath");
        MavenProject project = mojos.readMavenProject(basedir);
        MavenSession session = mojos.newMavenSession(project);
        MojoExecution execution = mojos.newMojoExecution();

        addDependency(project, "dependency", dependency);

        mojos.executeMojo(session, project, execution);

        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "classpath/Classpath.class");
    }

    @Test
    public void testClasspath_dependencySourceTypes_ignore() throws Exception {
        // dependency has both .java and .class files, but .java file is corrupted
        // assert the compiler uses .class file when dependencySourceTypes=ignore

        File dependency = compile("compile/basic");
        Files.writeString(
                new File(dependency, "target/classes/basic/Basic.java").toPath(), "corrupted", StandardCharsets.UTF_8);
        touch(new File(dependency, "target/classes/basic/Basic.java")); // javac will pick newer file by default

        File basedir = resources.getBasedir("compile/classpath");
        MavenProject project = mojos.readMavenProject(basedir);

        addDependency(project, "dependency", new File(dependency, "target/classes"));

        mojos.compile(project, newParameter("dependencySourceTypes", "ignore"));

        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "classpath/Classpath.class");
    }

    @Test
    public void testSpace() throws Exception {
        File basedir = compile("compile/spa ce");
        Assert.assertTrue(basedir.getAbsolutePath().contains(" "));
        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "space/Space.class");
    }

    @Test
    public void testError() throws Exception {
        ErrorMessage expected = new ErrorMessage(compilerId);
        expected.setSnippets("jdt", "ERROR Error.java [4:11] Foo cannot be resolved to a type");
        expected.setSnippets(
                "javac", "ERROR Error.java [4:11]", "cannot find symbol", "class Foo", "location", "class basic.Error");

        File basedir = resources.getBasedir("compile/error");
        try {
            compile(basedir);
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertBuildOutputs(basedir, new String[0]);
        mojos.assertMessage(basedir, "src/main/java/error/Error.java", expected);
    }

    @Test
    public void testWarn() throws Exception {
        File basedir = resources.getBasedir("compile/warn");
        compile(basedir); // no warnings by default
        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "warn/Warn.class");
        mojos.assertMessages(basedir, "src/main/java/warn/Warn.java", new String[0]);

        ErrorMessage expected = new ErrorMessage(compilerId);
        expected.setSnippets(
                "jdt",
                "WARNING Warn.java [5:3] List is a raw type. References to generic type List<E> should be parameterized");
        expected.setSnippets(
                "javac",
                "WARNING Warn.java [5:12] found raw type: java.util.List\n  missing type arguments for generic class java.util.List<E>");

        compile(basedir, newParameter("showWarnings", "true"));
        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "warn/Warn.class");
        mojos.assertMessage(basedir, "src/main/java/warn/Warn.java", expected);
    }

    @Test
    public void testEncoding() throws Exception {
        File basedir = resources.getBasedir("compile/encoding");
        try {
            compile(basedir, newParameter("encoding", "ISO-8859-5"));
            Assert.fail();
        } catch (MojoExecutionException e) {
            //
        }
        mojos.assertMessage(
                new File(basedir, "src/main/java/encoding/ISO8859p5.java"),
                "\u043f\u043e\u0440\u0443\u0441\u0441\u043a\u0438"); // "inrussian" in UTF8 Russian
    }

    @Test
    public void testParameters() throws Exception {
        File basedir = resources.getBasedir("compile/parameters");
        compile(basedir, newParameter("parameters", "true"), newParameter("source", "1.8"));
        assertThat(
                new File(basedir, "target/classes/parameters/MethodParameter.class"),
                hasMethodParameterWithName("myNamedParameter"));
    }

    @Test
    public void testEmpty() throws Exception {
        File basedir = compile("compile/empty");
        mojos.assertBuildOutputs(basedir, new String[0]);
        Assert.assertFalse("outputDirectory was not created", new File(basedir, "target/classes").exists());
    }

    @Test
    public void testIncludeNonJavaSources() throws Exception {
        File basedir = resources.getBasedir("compile/unexpected-sources");
        try {
            compile(basedir);
        } catch (MojoExecutionException e) {
            Assert.assertEquals("<includes> patterns must end with .java. Illegal patterns: [**]", e.getMessage());
        }
        mojos.assertBuildOutputs(basedir, new String[0]);
    }

    @Test
    public void testMultipleExecutions() throws Exception {
        File basedir = resources.getBasedir("compile/multiple-executions");
        MavenProject project = mojos.readMavenProject(basedir);
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
        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "other/Other.class");

        // compile main sources
        Xpp3Dom includes = new Xpp3Dom("includes");
        includes.addChild(newParameter("include", "main/*.java"));
        compile(basedir, includes);
        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "main/Main.class");
    }

    @Test
    public void testBinaryTypeMessage() throws Exception {
        // javac (tested with 1.8.0_05 and 1.7.0_45) produce warning messages for dependency .class files in some cases
        // the point of this test is to verify compile mojo tolerates this messages, i.e. does not fail
        // in this particular test, the message is triggered by missing @annotation referenced from a dependency class

        File basedir = resources.getBasedir("compile/binary-class-message");

        MavenProject project = mojos.readMavenProject(new File(basedir, "project"));
        MavenSession session = mojos.newMavenSession(project);
        MojoExecution execution = mojos.newMojoExecution();
        addDependency(project, "dependency", new File(basedir, "annotated.zip"));
        mojos.executeMojo(session, project, execution);
        mojos.assertBuildOutputs(new File(basedir, "project/target/classes"), "project/Project.class");
    }

    @Test
    public void testImplicitClassfileGeneration() throws Exception {
        // javac automatically generates class files from sources found on classpath in some cases
        // the point of this test is to make sure this behaviour is disabled

        File dependency = compile("compile/basic");
        cp(dependency, "src/main/java/basic/Basic.java", "target/classes/basic/Basic.java");
        touch(dependency, "target/classes/basic/Basic.java"); // must be newer than .class file

        File basedir = resources.getBasedir("compile/implicit-classfile");
        MavenProject project = mojos.readMavenProject(basedir);
        MavenSession session = mojos.newMavenSession(project);
        MojoExecution execution = mojos.newMojoExecution();

        addDependency(project, "dependency", new File(dependency, "target/classes"));

        mojos.executeMojo(session, project, execution);

        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "implicit/Implicit.class");
    }

    @Test
    public void testAnnotation() throws Exception {
        File basedir = compile("compile/annotation");

        assertThat(
                new File(basedir, "target/classes/annotation/AnnotatedClass.class"),
                hasAnnotation("annotation.Annotation"));
    }

    @Test
    @Ignore("The test is useful but needs to be reimplemented")
    public void testInnerTypeDependency_sourceDependencies() throws Exception {
        File basedir = resources.getBasedir("compile/inner-type-dependency");

        MavenProject project = mojos.readMavenProject(basedir);
        addDependency(project, "dependency", new File(basedir, "dependency/src/main/java"));

        mojos.compile(project, newParameter("dependencySourceTypes", "prefer"));
        mojos.assertBuildOutputs(new File(basedir, "target/classes"), "innertyperef/InnerTypeRef.class");
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

        File basedir = resources.getBasedir("compile/failOnError");
        try {
            compile("compile/failOnError");
            // Assert.fail();
        } catch (MojoExecutionException e) {
            //
        }
        mojos.assertMessage(basedir, "src/main/java/basic/Error.java", expected);
    }

    @Test
    public void testFailOnErrorFalse() throws Exception {
        try {
            File basedir = resources.getBasedir("compile/failOnError");
            compile("compile/failOnError", newParameter("failOnError", "false"));
            if (compilerId.equals(CompilerJdt.ID)) {
                mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
            }
        } catch (MojoExecutionException e) {
            Assert.fail();
        }
    }
}
