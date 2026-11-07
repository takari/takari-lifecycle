package io.takari.maven.plugins.compile;

import static io.takari.maven.testing.TestResources.assertFileContents;
import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.rm;
import static io.takari.maven.testing.TestResources.touch;
import static org.junit.Assert.fail;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;
import io.takari.maven.plugins.compile.javac.CompilerJavac;
import io.takari.maven.plugins.compile.jdt.CompilerJdt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Runs once per compiler via the {@code AnnotationProcessing*Test} subclasses, so surefire can run the compilers in
 * parallel JVMs.
 */
public abstract class AnnotationProcessingTest extends AbstractCompileTest {

    /**
     * The test processor compiled once per JVM: compiling it is setup, not what these tests test. Tests get a fresh
     * project copy with these classes copied in.
     */
    private static Path processorClasses;

    protected AnnotationProcessingTest(String compilerId) {
        super(compilerId);
    }

    private Path procCompile(String projectName, Proc proc, Xpp3Dom... parameters) throws Exception, IOException {
        Path basedir = resources.getBasedir(projectName).toPath();
        return procCompile(basedir, proc, parameters);
    }

    private Path procCompile(Path basedir, Proc proc, Xpp3Dom... parameters) throws Exception, IOException {
        Path processor = compileAnnotationProcessor();
        return processAnnotations(basedir, proc, processor, parameters);
    }

    private Path processAnnotations(Path basedir, Proc proc, Path processor, Xpp3Dom... parameters) throws Exception {
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        processAnnotations(project, processor, proc, parameters);
        return basedir;
    }

    protected void processAnnotations(MavenProject project, Path processor, Proc proc, Xpp3Dom... parameters)
            throws Exception {
        MavenSession session = mojos.newMavenSession(project);
        processAnnotations(session, project, "compile", processor, proc, parameters);
    }

    protected void processAnnotations(
            MavenSession session, MavenProject project, String goal, Path processor, Proc proc, Xpp3Dom... parameters)
            throws Exception {
        MojoExecution execution = mojos.newMojoExecution(goal);

        if (processor != null) {
            addDependency(project, "processor", processor.resolve("target/classes"));
        }

        Xpp3Dom configuration = execution.getConfiguration();

        if (proc != null) {
            configuration.addChild(newParameter("proc", proc.name()));
        }
        if (parameters != null) {
            for (Xpp3Dom parameter : parameters) {
                configuration.addChild(parameter);
            }
        }

        mojos.executeMojo(session, project, execution);
    }

    private Path compileAnnotationProcessor() throws Exception, IOException {
        Path processor = resources.getBasedir("compile-proc/processor").toPath();
        Path classes = processor.resolve("target/classes");
        synchronized (AnnotationProcessingTest.class) {
            if (processorClasses == null) {
                compile(processor.toFile());
                // unique per JVM: parallel surefire forks share target/
                processorClasses = Files.createTempDirectory(Path.of("target"), "processor-classes");
                FileUtils.copyDirectoryStructure(classes.toFile(), processorClasses.toFile());
            } else {
                FileUtils.copyDirectoryStructure(processorClasses.toFile(), classes.toFile());
            }
        }
        cp(
                processor.toFile(),
                "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
                "target/classes/META-INF/services/javax.annotation.processing.Processor");
        return processor;
    }

    @Test
    public void testProc_only() throws Exception {
        Path basedir = procCompile("compile-proc/proc", Proc.only);
        mojos.assertBuildOutputs(
                basedir.resolve("target/generated-sources/annotations"),
                "proc/GeneratedSource.java",
                "proc/AnotherGeneratedSource.java");
    }

    @Test
    public void testProc_none() throws Exception {
        Path basedir = procCompile("compile-proc/proc", Proc.none);
        mojos.assertBuildOutputs(basedir.resolve("target"), "classes/proc/Source.class");
    }

    @Test
    public void testProc_proc() throws Exception {
        Path basedir = procCompile("compile-proc/proc", Proc.proc);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
                "classes/proc/AnotherGeneratedSource.class");
    }

    @Test
    public void testProc_incrementalTypeIndex() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc").toPath();
        Path src = basedir.resolve("src/main/java");

        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
        mojos.assertBuildOutputs(basedir.resolve("target/classes"), "proc/Source.class", "types.lst");
        assertFileContents("proc.Source\n", basedir.toFile(), "target/classes/types.lst");

        // create new annotated source and run incremental build
        JavaFile.builder(
                        "proc", //
                        TypeSpec.classBuilder("AnotherSource") //
                                .addAnnotation(AnnotationSpec.builder(ClassName.get("processor", "Annotation"))
                                        .build()) //
                                .build()) //
                .build()
                .writeTo(src);
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
        mojos.assertBuildOutputs(
                basedir.resolve("target/classes"), "proc/Source.class", "proc/AnotherSource.class", "types.lst");
        assertFileContents("proc.AnotherSource\nproc.Source\n", basedir.toFile(), "target/classes/types.lst");

        // delete the new source and run incremental build
        rm(src.toFile(), "proc/AnotherSource.java");
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
        mojos.assertBuildOutputs(basedir.resolve("target/classes"), "proc/Source.class", "types.lst");
        mojos.assertDeletedOutputs(basedir.resolve("target/classes"), "proc/AnotherSource.class");
        assertFileContents("proc.Source\n", basedir.toFile(), "target/classes/types.lst");
    }

    @Test
    public void testProc_incrementalProcessorChange() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc").toPath();
        processAnnotations(basedir, Proc.proc, processor);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
                "classes/proc/AnotherGeneratedSource.class");

        rm(processor.toFile(), "target/classes/META-INF/services/javax.annotation.processing.Processor");
        mojos.flushClasspathCaches();
        processAnnotations(basedir, Proc.proc, processor);
        mojos.assertBuildOutputs(basedir.resolve("target"), "classes/proc/Source.class");
        mojos.assertDeletedOutputs(
                basedir.resolve("target"), //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
                "classes/proc/AnotherGeneratedSource.class");
    }

    @Test
    public void testProc_dummyOutput() throws Exception {
        Path basedir = procCompile("compile-proc/proc", Proc.proc, newProcessors("processor.Processor_dummyOutput"));
        mojos.assertBuildOutputs(basedir.resolve("target"), "classes/proc/Source.class");
    }

    @Test
    public void testProcTypeReference() throws Exception {
        Path basedir = procCompile("compile-proc/proc-type-reference", Proc.proc);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "classes/proc/GeneratedSourceSubclass.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
                "classes/proc/AnotherGeneratedSource.class");
    }

    @Test
    public void testProc_createResource() throws Exception {
        Path basedir = procCompile("compile-proc/proc", Proc.proc, newProcessors("processor.ProcessorCreateResource"));
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java");
    }

    // The following five tests help test the behavior of the full and incremental
    // compile strategies of the CompilerJdt with regards to Annotation Processing.
    // Incremental compile is triggered on the last two tests with a second compile step
    // to force apt to run in a course grained incremental manner.
    // Both final round only and during each round APT code generators are tested.

    /**
     * This test compiles with the a non-final round processor.
     *
     * There are no forward references that attempt to use the newly generated type.
     *
     * Note the use of "processor.Processor" and the "compile-proc/proc" project in this test.
     */
    @Test
    public void testProc_annotationProcessors() throws Exception {
        Xpp3Dom processors = newProcessors("processor.Processor");
        Path basedir = procCompile("compile-proc/proc", Proc.proc, processors);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");
    }

    /**
     * This test generates code in each round of an apt processor.
     *
     * There are is a forward reference which attempt to use the newly generated type as a super class.
     *
     * Any reference to the new type would be sufficient for this test.
     *
     * Note the use of "processor.Processor" and the "compile-proc/proc-type-reference" project in this test.
     */
    @Test
    public void testProcRef_annotationProcessors() throws Exception {
        Xpp3Dom processors = newProcessors("processor.Processor");
        Path basedir = procCompile("compile-proc/proc-type-reference", Proc.proc, processors);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "classes/proc/GeneratedSourceSubclass.class");
    }

    /**
     * This test generates code in the final round of an apt processor.
     *
     * There are is a forward reference which attempt to use the newly generated type as a super class.
     *
     * Any reference to the new type would be sufficient for this test.
     *
     * Note the use of "processor.ProcessorImplLastRound" and the "compile-proc/proc-type-reference" project in this test.
     */
    @Test
    public void testProcRef_annotationProcessors_LastRound() throws Exception {
        // javac can't handle forward references in the last round, but jdt can.
        Assume.assumeTrue(CompilerJdt.ID.equals(compilerId));
        Xpp3Dom processors = newProcessors("processor.ProcessorImplLastRound");
        Path basedir = procCompile("compile-proc/proc-type-reference", Proc.proc, processors);

        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "classes/proc/GeneratedSourceSubclass.class");
    }

    /**
     * This test generates code in each round of an apt processor.
     *
     * There are is a forward reference which attempt to use the newly generated type as a super class. Any reference to the new type would be sufficient for this test.
     *
     * This test the touches an annotated type and runs an incremental compile where much of the same code is forced to be recompiled.
     *
     * Note the use of "processor.Processor" and the "compile-proc/proc-type-reference" project in this test.
     */
    @Test
    public void testProcRef_annotationProcessors_recompile() throws Exception {
        Xpp3Dom processors = newProcessors("processor.Processor");
        Path basedir = procCompile("compile-proc/proc-type-reference", Proc.proc, processors);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "classes/proc/GeneratedSourceSubclass.class");

        touch(basedir.toFile(), "src/main/java/proc/Source.java");

        procCompile(basedir, Proc.proc, processors);

        String[] expectedOuput;
        if (CompilerJdt.ID.equals(compilerId)) {
            // incremental in jdt compile means that GeneratedSourceSubclass sees no
            // structural change and doesn't need to be recompiled.
            expectedOuput = new String[] { //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class"
            };
        } else {
            expectedOuput = new String[] { //
                // everything is recompiled by javac
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "classes/proc/GeneratedSourceSubclass.class"
            };
        }
        mojos.assertBuildOutputs(basedir.resolve("target"), expectedOuput);
    }

    /**
     * This test generates code in the final round of an apt processor.
     *
     * There are is a forward reference which attempt to use the newly generated type as a super class. Any reference to the new type would be sufficient for this test.
     *
     * This test the touches an annotated type and runs an incremental compile where much of the same code is forced to be recompiled.
     *
     * Note the use of "processor.ProcessorImplLastRound" and the "compile-proc/proc-type-reference" project in this test.
     */
    @Test
    public void testProcRef_annotationProcessors_LastRound_recompile() throws Exception {
        // javac can't handle forward references in the last round, but jdt can.
        Assume.assumeTrue(CompilerJdt.ID.equals(compilerId));
        Xpp3Dom processors = newProcessors("processor.ProcessorImplLastRound");
        Path basedir = procCompile("compile-proc/proc-type-reference", Proc.proc, processors);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "classes/proc/GeneratedSourceSubclass.class");

        touch(basedir.toFile(), "src/main/java/proc/Source.java");

        procCompile(basedir, Proc.proc, processors);

        // okay this is a little wonky. Since source was recompiled, we delete it's
        // apt generated type, which is referenced by GeneratedSourceSubclass, once it's
        // deleted we then generated the GeneratedSource, compile it, and then have to
        // recompile GeneratedSourceSubclass, hence it appears in this test and not
        // testProcRef_annotationProcessors_recompile's list of output for jdt.
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "classes/proc/GeneratedSourceSubclass.class");
    }

    @Test
    public void testProc_processorErrorMessage() throws Exception {
        Xpp3Dom processors = newProcessors("processor.ErrorMessageProcessor");
        Path basedir = resources.getBasedir("compile-proc/proc").toPath();
        try {
            procCompile(basedir, Proc.only, processors);
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertBuildOutputs(basedir.resolve("target"), new String[0]);

        ErrorMessage expected = new ErrorMessage(compilerId);
        expected.setSnippets("jdt", "ERROR Source.java [6:14] test error message"); // TODO why 14?
        expected.setSnippets("javac", "ERROR Source.java [6:8] test error message");
        mojos.assertMessage(basedir, "src/main/java/proc/Source.java", expected);

        Collection<String> pomMessages = mojos.getBuildContextLog()
                .getMessages(basedir.resolve("pom.xml").toFile());
        Assert.assertEquals(pomMessages.toString(), 3, pomMessages.size());
        // TODO assert actual messages are as expected
    }

    @Test
    public void testProc_messages() throws Exception {
        ErrorMessage expected = new ErrorMessage(compilerId);
        expected.setSnippets("javac", "ERROR BrokenSource.java [2:29]", "cannot find symbol");
        expected.setSnippets("jdt", "ERROR BrokenSource.java [2:29]", "cannot be resolved to a type");

        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc").toPath();

        String[] outputs;
        if (CompilerJdt.ID.equals(compilerId)) {
            outputs = new String[] {
                "classes/proc/Source.class" //
                ,
                "generated-sources/annotations/proc/BrokenSource.java"
            };
        } else {
            // TODO investigate why javac does not generate classes/proc/Source.class
            outputs = new String[] {"generated-sources/annotations/proc/BrokenSource.java"};
        }

        Xpp3Dom processors = newProcessors("processor.BrokenProcessor");
        try {
            processAnnotations(basedir, Proc.proc, processor, processors);
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertBuildOutputs(basedir.resolve("target"), outputs);
        assertProcMessage(basedir, "target/generated-sources/annotations/proc/BrokenSource.java", expected);

        // no change rebuild should produce the same messages
        try {
            processAnnotations(basedir, Proc.proc, processor, processors);
            Assert.fail();
        } catch (MojoExecutionException e) {
            // expected
        }
        mojos.assertCarriedOverOutputs(basedir.resolve("target"), outputs);
        assertProcMessage(basedir, "target/generated-sources/annotations/proc/BrokenSource.java", expected);
    }

    private void assertProcMessage(Path basedir, String path, ErrorMessage expected) throws Exception {
        // javac reports the same compilation error twice when Proc.proc
        Set<String> messages = new HashSet<String>(
                mojos.getBuildContextLog().getMessages(basedir.resolve(path).toFile()));
        Assert.assertEquals(messages.toString(), 1, messages.size());
        String message = messages.iterator().next();
        Assert.assertTrue(expected.isMatch(message));
    }

    @Test
    public void testProc_processorOptions() throws Exception {
        Xpp3Dom processors = newProcessors("processor.ProcessorWithOptions");

        Xpp3Dom options = new Xpp3Dom("annotationProcessorOptions");
        options.addChild(newParameter("optionA", "valueA"));
        options.addChild(newParameter("optionB", "valueB"));
        procCompile("compile-proc/proc", Proc.proc, processors, options);
    }

    @Test
    public void testProc_staleGeneratedSourcesCleanup() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc").toPath();

        processAnnotations(basedir, Proc.proc, processor);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
                "classes/proc/AnotherGeneratedSource.class");

        // remove annotation
        cp(basedir.toFile(), "src/main/java/proc/Source.java-remove-annotation", "src/main/java/proc/Source.java");
        processAnnotations(basedir, Proc.proc, processor);
        mojos.assertDeletedOutputs(
                basedir.resolve("target"), //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class", //
                "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
                "classes/proc/AnotherGeneratedSource.class");
    }

    @Test
    public void testProc_incrementalDeleteLastAnnotatedSource() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc").toPath();

        Xpp3Dom processors = newProcessors("processor.Processor");

        // initial compilation
        processAnnotations(basedir, Proc.proc, processor, processors);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");

        // no-change rebuild
        processAnnotations(basedir, Proc.proc, processor, processors);
        mojos.assertCarriedOverOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");

        // remove annotated class
        rm(basedir.toFile(), "src/main/java/proc/Source.java");
        processAnnotations(basedir, Proc.proc, processor, processors);
        mojos.assertDeletedOutputs(
                basedir.resolve("target"), //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/Source.class", //
                "classes/proc/GeneratedSource.class");
    }

    @Test
    public void testProc_nonIncrementalProcessor_onlyEX_deleteSource() throws Exception {
        Path processor = compileAnnotationProcessor();

        Path basedir = resources.getBasedir("compile-proc/proc").toPath();
        Path target = basedir.resolve("target");

        Xpp3Dom processors = newProcessors("processor.NonIncrementalProcessor");

        processAnnotations(basedir, Proc.only, processor, processors);
        mojos.assertBuildOutputs(
                target, //
                "generated-sources/annotations/proc/NonIncrementalSource.java");

        rm(basedir.toFile(), "src/main/java/proc/Source.java");

        processAnnotations(basedir, Proc.only, processor, processors);
        mojos.assertDeletedOutputs(
                target, //
                "generated-sources/annotations/proc/NonIncrementalSource.java");
    }

    @Test
    public void testProc_projectSourceRoots() throws Exception {
        Path processor = compileAnnotationProcessor();

        Path basedir = resources.getBasedir("compile-proc/proc").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        addDependency(project, "processor", processor.resolve("target/classes"));

        mojos.compile(project, newParameter("proc", Proc.proc.name()), newProcessors("processor.Processor"));

        Assert.assertTrue(project.getCompileSourceRoots()
                .contains(basedir.resolve("target/generated-sources/annotations")
                        .toAbsolutePath()
                        .toString()));

        // TODO testCompile
    }

    @Test
    public void testIncrementalDelete() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir =
                resources.getBasedir("compile-proc/proc-incremental-delete").toPath();

        Xpp3Dom processors = newProcessors("processor.Processor");

        // initial compilation
        processAnnotations(basedir, Proc.proc, processor, processors);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Keep.class", //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");

        // no-change rebuild
        processAnnotations(basedir, Proc.proc, processor, processors);
        mojos.assertCarriedOverOutputs(
                basedir.resolve("target"), //
                "classes/proc/Keep.class", //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");

        // remove annotated source
        rm(basedir.toFile(), "src/main/java/proc/Source.java");
        processAnnotations(basedir, Proc.proc, processor, processors);
        mojos.assertDeletedOutputs(
                basedir.resolve("target"), //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/Source.class", //
                "classes/proc/GeneratedSource.class");
    }

    @Test
    public void testConvertGeneratedSourceToHandwritten() throws Exception {
        // this test demonstrates the following scenario
        // 1. annotation processor generates java source and the generated source is compiled by the compiler
        // 2. annotation is removed from original source and the generated source is moved to a dependency
        // assert original generatedSource.java and generatedSource.class are deleted

        Path processor = compileAnnotationProcessor();
        Path basedir =
                resources.getBasedir("compile-proc/proc-incremental-move").toPath();
        Path moduleA = basedir.resolve("module-a");
        Path moduleB = basedir.resolve("module-b");

        Xpp3Dom processors = newProcessors("processor.Processor");

        mojos.compile(moduleB.toFile());
        MavenProject projectA = mojos.readMavenProject(moduleA.toFile());
        addDependency(projectA, "module-b", moduleB.resolve("target/classes"));
        processAnnotations(projectA, processor, Proc.proc, processors);
        mojos.assertBuildOutputs(
                moduleA.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");

        mojos.flushClasspathCaches();

        // move generated source to module-b/src/main/java
        cp(
                moduleB.toFile(),
                "src/main/java/proc/GeneratedSource.java-moved",
                "src/main/java/proc/GeneratedSource.java");
        cp(moduleA.toFile(), "src/main/java/modulea/ModuleA.java-new", "src/main/java/modulea/ModuleA.java");
        cp(moduleA.toFile(), "src/main/java/proc/Source.java-remove-annotation", "src/main/java/proc/Source.java");
        mojos.compile(moduleB.toFile());
        mojos.assertBuildOutputs(moduleB, "target/classes/proc/GeneratedSource.class");
        projectA = mojos.readMavenProject(moduleA.toFile());
        addDependency(projectA, "module-b", moduleB.resolve("target/classes"));
        processAnnotations(projectA, processor, Proc.proc, processors);
        mojos.assertBuildOutputs(
                moduleA.resolve("target"), //
                "classes/proc/Source.class",
                "classes/modulea/ModuleA.class");
        mojos.assertDeletedOutputs(
                moduleA.resolve("target"), //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");
    }

    private Xpp3Dom newProcessors(String... processors) {
        Xpp3Dom annotationProcessors = new Xpp3Dom("annotationProcessors");
        for (String processor : processors) {
            annotationProcessors.addChild(newParameter("processor", processor));
        }
        return annotationProcessors;
    }

    @Test
    public void testRequireProc() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/require-proc").toPath();
        try {
            processAnnotations(basedir, null, processor);
            Assert.fail();
        } catch (IllegalArgumentException expected) {
            // TODO assert message
        }

        processAnnotations(basedir, null, null);
    }

    @Test
    public void testRequireProc_processorpathMasksClasspath() throws Exception {
        // assert annotation processors present on project classpath are ignored when processorpath is configured

        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/require-proc").toPath();
        processAnnotations(basedir, null, processor, new Xpp3Dom("processorpath"));
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class");
    }

    @Test
    public void testRequireProc_processorpath() throws Exception {
        MavenProject annotations =
                mojos.readMavenProject(resources.getBasedir("compile-proc/processorpath-annotation"));
        mojos.compile(annotations);

        MavenProject processor = mojos.readMavenProject(resources.getBasedir("compile-proc/processorpath-processor"));
        mojos.newDependency(annotations
                        .getBasedir()
                        .toPath()
                        .resolve("target/classes")
                        .toFile())
                .setArtifactId("annotations")
                .addTo(processor);
        mojos.compile(processor);
        cp(
                processor.getBasedir(),
                "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
                "target/classes/META-INF/services/javax.annotation.processing.Processor");

        Path basedir = resources.getBasedir("compile-proc/proc").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        mojos.newDependency(annotations
                        .getBasedir()
                        .toPath()
                        .resolve("target/classes")
                        .toFile())
                .setArtifactId("annotations")
                .addTo(project);

        MavenSession session = mojos.newMavenSession(project);
        SimpleReactorReader.builder() //
                .addProjects(annotations, processor, project) //
                .toSession(session.getRepositorySession());

        session.setProjects(Arrays.asList(annotations, processor, project));
        session.setCurrentProject(project);
        Xpp3Dom processorpath = new Xpp3Dom("processorpath");
        processorpath.addChild(newProcessorpathEntry(processor));
        try {
            mojos.compile(session, project, processorpath);
            fail();
        } catch (IllegalArgumentException expected) {
            // TODO assert message
        }
    }

    @Test
    public void testRecompile() throws Exception {

        /**
         * <pre>
         *    Source.java -> Source.class
         *      \-> GeneratedSource.java -> GeneratedSource.class
         *                                      ^
         *                                Client.java -> Client.class
         * </pre>
         */
        Path processor = compileAnnotationProcessor();
        Path basedir =
                resources.getBasedir("compile-proc/proc-incremental-recompile").toPath();

        Xpp3Dom processors = newProcessors("processor.ProcessorSiblingBody");
        Xpp3Dom options = new Xpp3Dom("annotationProcessorOptions");
        options.addChild(newParameter(
                "basedir", basedir.resolve("src/main/java").toRealPath().toString()));

        processAnnotations(basedir, Proc.proc, processor, processors, options);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "classes/proc/Client.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");

        cp(
                basedir.toFile(),
                "src/main/java/proc/GeneratedSource.body-changed",
                "src/main/java/proc/GeneratedSource.body");
        touch(basedir.toFile(), "src/main/java/proc/Source.java");
        processAnnotations(basedir, Proc.proc, processor, processors, options);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "classes/proc/Client.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");
    }

    @Test
    public void testProc_processorLastRound() throws Exception {
        Xpp3Dom processors = newProcessors("processor.ProcessorLastRound");
        Path basedir = procCompile("compile-proc/proc", Proc.only, processors);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/types.lst");

        assertFileContents("proc.Source\n", basedir.toFile(), "target/classes/types.lst");
    }

    @Test
    public void testIncremental_proc_only() throws Exception {
        // the point of this test is to assert that changes to annotations trigger affected sources reprocessing when
        // proc=only
        // note sourcepath=disable, otherwise proc:only is all-or-nothing

        // TODO this test likely becomes redundant when annotation processing is always all-or-nothing

        Path processor = compileAnnotationProcessor();
        Path basedir =
                resources.getBasedir("compile-proc/proc-incremental-proconly").toPath();
        Path generatedSources = basedir.resolve("target/generated-sources/annotations");

        Xpp3Dom processors = newProcessors("processor.Processor");

        compile(basedir, processor, "compile-only");
        processAnnotations(basedir, Proc.only, processor, processors, newParameter("sourcepath", "disable"));
        mojos.assertBuildOutputs(
                generatedSources,
                "proc/GeneratedConcrete.java",
                "proc/GeneratedAbstract.java",
                "proc/GeneratedAnother.java");

        cp(basedir.toFile(), "src/main/java/proc/Abstract.java-remove-annotation", "src/main/java/proc/Abstract.java");
        compile(basedir, processor, "compile-only");
        processAnnotations(basedir, Proc.only, processor, processors, newParameter("sourcepath", "disable"));
        mojos.assertDeletedOutputs(generatedSources, "proc/GeneratedConcrete.java", "proc/GeneratedAbstract.java");
        if (CompilerJdt.ID.equals(compilerId)) {
            // annotation processing is always all-or-nothing, all outputs are always recreated
            // mojos.assertCarriedOverOutputs(generatedSources, "proc/GeneratedAnother.java");
            mojos.assertBuildOutputs(generatedSources, "proc/GeneratedAnother.java");
        } else {
            mojos.assertBuildOutputs(generatedSources, "proc/GeneratedAnother.java");
        }
    }

    private void compile(Path basedir, Path processor, String executionId) throws Exception {
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);
        MojoExecution execution = mojos.newMojoExecution();
        MojoExecution cloned = new MojoExecution(execution.getMojoDescriptor(), executionId, null);
        cloned.setConfiguration(execution.getConfiguration());
        execution.getConfiguration().addChild(newParameter("proc", Proc.none.name()));
        addDependency(project, "processor", processor.resolve("target/classes"));
        mojos.executeMojo(session, project, cloned);
    }

    @Test
    public void testMutliround_procOnly() throws Exception {
        Path basedir = procCompile("compile-proc/multiround", Proc.only);
        Path generatedSources = basedir.resolve("target/generated-sources/annotations");

        mojos.assertMessages(basedir.toFile(), "src/main/java/multiround/Source.java", new String[] {});
        mojos.assertBuildOutputs(
                generatedSources, "multiround/GeneratedSource.java", "multiround/AnotherGeneratedSource.java");
    }

    @Test
    public void testMultiround_processorLastRound() throws Exception {
        // processor.ProcessorLastRound creates well-known resource during last round
        // the point of this test is to assert this works during incremental build
        // when compiler may be invoked several times to compile indirectly affected sources

        Path processor = compileAnnotationProcessor();
        Path basedir =
                resources.getBasedir("compile-proc/multiround-type-reference").toPath();

        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "classes/proc/AnotherSource.class", //
                "classes/types.lst");

        cp(basedir.toFile(), "src/main/java/proc/Source.java-changed", "src/main/java/proc/Source.java");
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "classes/proc/AnotherSource.class", //
                "classes/types.lst");
    }

    @Test
    public void testLastRound_typeIndex() throws Exception {
        // apparently javac can't resolve "forward" references to types generated during apt last round
        Assume.assumeTrue(CompilerJdt.ID.equals(compilerId));

        Xpp3Dom processors = newProcessors("processor.ProcessorLastRound_typeIndex");
        Path basedir = procCompile("compile-proc/multiround-type-index", Proc.proc, processors);
        Path target = basedir.resolve("target");
        mojos.assertBuildOutputs(
                target, //
                "generated-sources/annotations/generated/TypeIndex.java", //
                "generated-sources/annotations/generated/TypeIndex2.java", //
                "classes/generated/TypeIndex.class", //
                "classes/generated/TypeIndex2.class", //
                "classes/typeindex/Annotated.class", //
                "classes/typeindex/Consumer.class" //
                );
    }

    @Test
    public void testReprocess() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/reprocess").toPath();
        Path target = basedir.resolve("target");

        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProessorValue"));
        mojos.assertBuildOutputs(
                target, //
                "classes/reprocess/Annotated.class", //
                "classes/reprocess/Annotated.value", //
                "classes/reprocess/SimpleA.class", //
                "classes/reprocess/SimpleB.class");

        Assert.assertEquals("1", Files.readString(target.resolve("classes/reprocess/Annotated.value")));

        cp(basedir.toFile(), "src/main/java/reprocess/SimpleA.java-changed", "src/main/java/reprocess/SimpleA.java");
        touch(basedir.toFile(), "src/main/java/reprocess/Annotated.java");

        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProessorValue"));
        mojos.assertBuildOutputs(
                target, //
                "classes/reprocess/Annotated.class", //
                "classes/reprocess/Annotated.value", //
                "classes/reprocess/SimpleA.class", //
                "classes/reprocess/SimpleB.class");

        Assert.assertEquals("10", Files.readString(target.resolve("classes/reprocess/Annotated.value")));
    }

    @Test
    public void testProc_nonIncrementalProcessor() throws Exception {
        Assume.assumeTrue(CompilerJdt.ID.equals(compilerId));

        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc").toPath();
        Path target = basedir.resolve("target");

        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.NonIncrementalProcessor"));
        mojos.assertBuildOutputs(
                target, //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/NonIncrementalSource.java", //
                "classes/proc/NonIncrementalSource.class");

        FileUtils.deleteDirectory(target.toFile());
        processAnnotations(basedir, Proc.only, processor, newProcessors("processor.NonIncrementalProcessor"));
        mojos.assertBuildOutputs(
                target, //
                "generated-sources/annotations/proc/NonIncrementalSource.java");

        FileUtils.deleteDirectory(target.toFile());
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.NonIncrementalProcessor"));
        mojos.assertBuildOutputs(
                target, //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/NonIncrementalSource.java", //
                "classes/proc/NonIncrementalSource.class");

        FileUtils.deleteDirectory(target.toFile());
        processAnnotations(basedir, Proc.only, processor, newProcessors("processor.NonIncrementalProcessor"));
        mojos.assertBuildOutputs(
                target, //
                "generated-sources/annotations/proc/NonIncrementalSource.java");
    }

    @Test
    public void testProc_processorpath() throws Exception {
        MavenProject annotations =
                mojos.readMavenProject(resources.getBasedir("compile-proc/processorpath-annotation"));
        mojos.compile(annotations);

        MavenProject processor = mojos.readMavenProject(resources.getBasedir("compile-proc/processorpath-processor"));
        mojos.newDependency(annotations
                        .getBasedir()
                        .toPath()
                        .resolve("target/classes")
                        .toFile())
                .setArtifactId("annotations")
                .addTo(processor);
        mojos.compile(processor);
        cp(
                processor.getBasedir(),
                "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
                "target/classes/META-INF/services/javax.annotation.processing.Processor");

        Path basedir = resources.getBasedir("compile-proc/proc").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        mojos.newDependency(annotations
                        .getBasedir()
                        .toPath()
                        .resolve("target/classes")
                        .toFile())
                .setArtifactId("annotations")
                .addTo(project);

        MavenSession session = mojos.newMavenSession(project);
        SimpleReactorReader.builder() //
                .addProjects(annotations, processor, project) //
                .toSession(session.getRepositorySession());

        session.setProjects(Arrays.asList(annotations, processor, project));
        session.setCurrentProject(project);
        Xpp3Dom processorpath = new Xpp3Dom("processorpath");
        processorpath.addChild(newProcessorpathEntry(processor));
        mojos.compile(session, project, newParameter("proc", "proc"), processorpath);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class", //
                "generated-sources/annotations/proc/GeneratedSource.java", //
                "classes/proc/GeneratedSource.class");
    }

    @Test
    public void testProc_emptyProcessorPath() throws Exception {
        Path basedir = procCompile("compile-proc/proc", Proc.proc, new Xpp3Dom("processorpath"));
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class");
    }

    @Test
    public void testProc_incrementalTypeJump() throws Exception {
        // assert incremental behaviour when processor uses Elements to access non-annotated types
        // jdt is expected to run annotation processor if referenced non-annotated types change
        // (obviously still need to run apt when annotated types change, but that is tested elsewhere)

        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc").toPath();
        Path src = basedir.resolve("src/main/java");

        // processor references non-existing type proc.SourceJump
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.TypeJumpingProcessor"));
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc/Source.class");

        // create proc.SourceJump, assert apt ran as expected
        JavaFile.builder("proc", TypeSpec.classBuilder("SourceJump").build())
                .build()
                .writeTo(src);
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.TypeJumpingProcessor"));
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "classes/proc.SourceJump", // the generated resource
                "classes/proc/Source.class", //
                "classes/proc/SourceJump.class" //
                );

        // create proc.Unrelated, assert apt did NOT run
        JavaFile.builder("proc", TypeSpec.classBuilder("Unrelated").build())
                .build()
                .writeTo(src);
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.TypeJumpingProcessor"));
        if (CompilerJdt.ID.equals(compilerId)) {
            // jdt is supposed to compile the new type but carry-over everything else
            mojos.assertBuildOutputs(
                    basedir.resolve("target"), //
                    "classes/proc/Unrelated.class");
            mojos.assertDeletedOutputs(basedir.resolve("target"), new String[0]);
            mojos.assertCarriedOverOutputs(
                    basedir.resolve("target"), //
                    "classes/proc.SourceJump", // the generated resource
                    "classes/proc/Source.class", //
                    "classes/proc/SourceJump.class" //
                    );
        } else {
            // javac recompiles everything
            mojos.assertBuildOutputs(
                    basedir.resolve("target"), //
                    "classes/proc.SourceJump", // the generated resource
                    "classes/proc/Source.class", //
                    "classes/proc/SourceJump.class", //
                    "classes/proc/Unrelated.class" //
                    );
        }

        new Object();
    }

    private Xpp3Dom newProcessorpathEntry(MavenProject processor) {
        Xpp3Dom entry = new Xpp3Dom("processor");
        entry.addChild(newParameter("groupId", processor.getGroupId()));
        entry.addChild(newParameter("artifactId", processor.getArtifactId()));
        entry.addChild(newParameter("version", processor.getVersion()));
        return entry;
    }

    @Test
    public void testSourcepathDependency() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc-sourcepath").toPath();

        Path dependencyBasedir = basedir.resolve("dependency");
        Path projectBasedir = basedir.resolve("project");

        Xpp3Dom processors = newProcessors("processor.Processor");
        Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

        MavenProject dependency = mojos.readMavenProject(dependencyBasedir.toFile());
        MavenProject project = mojos.readMavenProject(projectBasedir.toFile());

        mojos.newDependency(dependencyBasedir.resolve("target/classes").toFile()) //
                .setGroupId(dependency.getGroupId()) //
                .setArtifactId(dependency.getArtifactId()) //
                .setVersion(dependency.getVersion()) //
                .addTo(project);

        MavenSession session = mojos.newMavenSession(project);
        session.setProjects(Arrays.asList(project, dependency));

        processAnnotations(session, project, "compile", processor, Proc.only, processors, sourcepath);
        mojos.assertBuildOutputs(
                projectBasedir.resolve("target"), //
                "generated-sources/annotations/sourcepath/project/GeneratedSource.java" //
                );
    }

    @Test
    public void testSourcepathDependencyNestedTypeThroughBinaryDependency() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources
                .getBasedir("compile-proc/proc-sourcepath-nestedtype-through-binary")
                .toPath();

        Path dependencyBasedir = basedir.resolve("dependency");
        Path binaryDependencyBasedir = basedir.resolve("binary-dependency");
        Path projectBasedir = basedir.resolve("project");

        Xpp3Dom processors = newProcessors("processor.EnclosedElementsProcessor");
        Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

        MavenProject dependency = mojos.readMavenProject(dependencyBasedir.toFile());
        MavenProject project = mojos.readMavenProject(projectBasedir.toFile());

        mojos.newDependency(binaryDependencyBasedir
                        .resolve("target/binary-dependency-1.0.jar")
                        .toFile())
                .addTo(project);

        mojos.newDependency(dependencyBasedir.resolve("target/classes").toFile()) //
                .setGroupId(dependency.getGroupId()) //
                .setArtifactId(dependency.getArtifactId()) //
                .setVersion(dependency.getVersion()) //
                .addTo(project);

        MavenSession session = mojos.newMavenSession(project);
        session.setProjects(Arrays.asList(project, dependency));

        processAnnotations(session, project, "compile", processor, Proc.only, processors, sourcepath);
        mojos.assertBuildOutputs(
                projectBasedir.resolve("target"), //
                "generated-sources/annotations/sourcepath/nestedtype/project/GeneratedSource.java" //
                );
    }

    @Test
    public void testSourcepathDependency_incremental() throws Exception {
        // the point of this test is assert that changes to sourcepath files are expected to trigger reprocessing of
        // affected sources

        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc-sourcepath").toPath();

        Path dependencyBasedir = basedir.resolve("dependency");
        Path projectBasedir = basedir.resolve("project");

        Xpp3Dom processors = newProcessors("processor.Processor");
        Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

        MavenProject dependency = mojos.readMavenProject(dependencyBasedir.toFile());
        MavenProject project = mojos.readMavenProject(projectBasedir.toFile());

        mojos.newDependency(dependencyBasedir.resolve("target/classes").toFile()) //
                .setGroupId(dependency.getGroupId()) //
                .setArtifactId(dependency.getArtifactId()) //
                .setVersion(dependency.getVersion()) //
                .addTo(project);

        MavenSession session = mojos.newMavenSession(project);
        session.setProjects(Arrays.asList(project, dependency));

        processAnnotations(session, project, "compile", processor, Proc.only, processors, sourcepath);
        mojos.assertBuildOutputs(
                projectBasedir.resolve("target"), //
                "generated-sources/annotations/sourcepath/project/GeneratedSource.java" //
                );

        // second, incremental, compilation with one of sourcepath files removed

        rm(dependencyBasedir.toFile(), "src/main/java/sourcepath/dependency/SourcepathDependency.java");
        mojos.flushClasspathCaches();

        dependency = mojos.readMavenProject(dependencyBasedir.toFile());
        project = mojos.readMavenProject(projectBasedir.toFile());

        mojos.newDependency(dependencyBasedir.resolve("target/classes").toFile()) //
                .setGroupId(dependency.getGroupId()) //
                .setArtifactId(dependency.getArtifactId()) //
                .setVersion(dependency.getVersion()) //
                .addTo(project);

        session = mojos.newMavenSession(project);
        session.setProjects(Arrays.asList(project, dependency));
        try {
            processAnnotations(session, project, "compile", processor, Proc.only, processors, sourcepath);
        } catch (MojoExecutionException expected) {
        }
        ErrorMessage message = new ErrorMessage(compilerId);
        message.setSnippets(CompilerJdt.ID, "sourcepath.dependency.SourcepathDependency cannot be resolved to a type");
        message.setSnippets(CompilerJavac.ID, "package sourcepath.dependency does not exist");
        mojos.assertMessage(projectBasedir, "src/main/java/sourcepath/project/Source.java", message);
        // oddly enough, both jdt and javac generate GeneratedSource despite the error
    }

    @Test
    public void testSourcepathDependency_classifiedDependency() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc-sourcepath").toPath();

        Path dependencyBasedir = basedir.resolve("dependency");
        Path projectBasedir = basedir.resolve("project");

        Xpp3Dom processors = newProcessors("processor.Processor");
        Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

        MavenProject dependency = mojos.readMavenProject(dependencyBasedir.toFile());
        MavenProject project = mojos.readMavenProject(projectBasedir.toFile());

        mojos.newDependency(dependencyBasedir.resolve("target/classes").toFile()) //
                .setGroupId(dependency.getGroupId()) //
                .setArtifactId(dependency.getArtifactId()) //
                .setVersion(dependency.getVersion()) //
                .setClassifier("classifier") //
                .addTo(project);

        MavenSession session = mojos.newMavenSession(project);
        session.setProjects(Arrays.asList(project, dependency));

        try {
            processAnnotations(session, project, "compile", processor, Proc.only, processors, sourcepath);
            Assert.fail();
        } catch (MojoExecutionException expected) {
            Assert.assertTrue(
                    expected.getMessage().contains(dependency.getGroupId() + ":" + dependency.getArtifactId()));
        }
    }

    @Test
    public void testSourcepathIncludes() throws Exception {
        Xpp3Dom includes = new Xpp3Dom("includes");
        includes.addChild(newParameter("include", "sourcepath/project/*.java"));
        Xpp3Dom processors = newProcessors("processor.Processor");
        Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");
        Path basedir =
                procCompile("compile-proc/proc-sourcepath-includes", Proc.only, includes, processors, sourcepath);
        mojos.assertBuildOutputs(
                basedir.resolve("target"), //
                "generated-sources/annotations/sourcepath/project/GeneratedSource.java" //
                );
    }

    @Test
    public void testSourcepathDependency_testCompile() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir("compile-proc/proc-sourcepath").toPath();

        Path dependencyBasedir = basedir.resolve("dependency");
        Path projectBasedir = basedir.resolve("project");

        Xpp3Dom processors = newProcessors("processor.Processor");
        Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

        MavenProject dependency = mojos.readMavenProject(dependencyBasedir.toFile());
        MavenProject project = mojos.readMavenProject(projectBasedir.toFile());

        mojos.newDependency(dependencyBasedir.resolve("target/classes").toFile()) //
                .setGroupId(dependency.getGroupId()) //
                .setArtifactId(dependency.getArtifactId()) //
                .setVersion(dependency.getVersion()) //
                .addTo(project);

        mojos.newDependency(dependencyBasedir.resolve("target/test-classes").toFile()) //
                .setGroupId(dependency.getGroupId()) //
                .setArtifactId(dependency.getArtifactId()) //
                .setType("test-jar") //
                .setVersion(dependency.getVersion()) //
                .addTo(project);

        MavenSession session = mojos.newMavenSession(project);
        session.setProjects(Arrays.asList(project, dependency));

        processAnnotations(session, project, "testCompile", processor, Proc.only, processors, sourcepath);
        mojos.assertBuildOutputs(
                projectBasedir.resolve("target"), //
                "generated-test-sources/test-annotations/sourcepath/project/test/GeneratedSourceTest.java" //
                );
    }

    @Test
    public void testSourcepath_classpathVisibility() throws Exception {
        Assume.assumeTrue(CompilerJdt.ID.equals(compilerId));

        Path basedir = resources.getBasedir().toPath();
        Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");
        Xpp3Dom classpathVisibility = newParameter("privatePackageReference", "error");
        try {
            procCompile(basedir, Proc.only, sourcepath, classpathVisibility);
            Assert.fail();
        } catch (MojoExecutionException expected) {
            Assert.assertTrue(expected.getMessage().contains("privatePackageReference"));
        }
    }

    @Test
    @Ignore("Neither javac nor jdt support secondary types on sourcepath")
    public void testSourcepathSecondatytype() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources
                .getBasedir("compile-proc/proc-sourcepath-secondarytype")
                .toPath();

        Path dependencyBasedir = basedir.resolve("dependency");
        Path projectBasedir = basedir.resolve("project");

        Xpp3Dom processors = newProcessors("processor.Processor");
        Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

        MavenProject dependency = mojos.readMavenProject(dependencyBasedir.toFile());
        MavenProject project = mojos.readMavenProject(projectBasedir.toFile());

        mojos.newDependency(dependencyBasedir.resolve("target/classes").toFile()) //
                .setGroupId(dependency.getGroupId()) //
                .setArtifactId(dependency.getArtifactId()) //
                .setVersion(dependency.getVersion()) //
                .addTo(project);

        MavenSession session = mojos.newMavenSession(project);
        session.setProjects(Arrays.asList(project, dependency));

        processAnnotations(session, project, "compile", processor, Proc.only, processors, sourcepath);
    }

    @Test
    public void testSourcepathCache() throws Exception {
        // project-a has its generated sources directory configured as compile source root
        // project-b depends on project-a generated sources
        // both project-a and project-b run proc=only sourcepath=reactorDependencies

        Path processor = compileAnnotationProcessor();
        Path basedir =
                resources.getBasedir("compile-proc/proc-sourcepath-cache").toPath();

        Xpp3Dom processors = newProcessors("processor.Processor");
        Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");
        Xpp3Dom procOnly = newParameter("proc", Proc.only.name());

        // setting up project-a
        Path projectaBasedir = basedir.resolve("project-a");
        MavenProject projecta = mojos.readMavenProject(projectaBasedir.toFile());
        addDependency(projecta, "processor", processor.resolve("target/classes"));
        projecta.addCompileSourceRoot(projectaBasedir
                .resolve("target/generated-sources/annotations")
                .toAbsolutePath()
                .normalize()
                .toString());

        // setting up project-b
        Path projectbBasedir = basedir.resolve("project-b");
        MavenProject projectb = mojos.readMavenProject(projectbBasedir.toFile());
        addDependency(projectb, "processor", processor.resolve("target/classes"));
        mojos.newDependency(projectaBasedir.resolve("target/classes").toFile()) //
                .setGroupId(projecta.getGroupId()) //
                .setArtifactId(projecta.getArtifactId()) //
                .setVersion(projecta.getVersion()) //
                .addTo(projectb);

        MavenSession session = mojos.newMavenSession(projecta);
        session.setProjects(Arrays.asList(projecta, projecta));

        // process annotations in project-a
        mojos.executeMojo(session, projecta, "compile", procOnly, processors, sourcepath);
        mojos.assertBuildOutputs(
                projectaBasedir.resolve("target"), //
                "generated-sources/annotations/sourcepath/projecta/GeneratedSourceA.java" //
                );

        // process annotations in project-b, requires access to project-a generated sources
        session.setCurrentProject(projectb);
        mojos.executeMojo(session, projectb, "compile", procOnly, processors, sourcepath);
        mojos.assertBuildOutputs(
                projectbBasedir.resolve("target"), //
                "generated-sources/annotations/sourcepath/projectb/GeneratedSourceB.java" //
                );
    }

    @Test
    public void testAnnotatedMember_incrementalProcessingTrigger() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir().toPath();
        Path src = basedir.resolve("src/main/java");

        // initial build, writes annotated field path is written to elements.lst file
        AnnotationSpec annotation =
                AnnotationSpec.builder(ClassName.get("processor", "Annotation")).build();
        JavaFile.builder(
                        "proc", //
                        TypeSpec.classBuilder("AnnotatedMemberSource") //
                                .addField(FieldSpec.builder(String.class, "annotatedfield")
                                        .addAnnotation(annotation)
                                        .build()) //
                                .build()) //
                .build()
                .writeTo(src);
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ElementsListProcessor"));
        mojos.assertBuildOutputs(basedir.resolve("target/classes"), "proc/AnnotatedMemberSource.class", "elements.lst");
        assertFileContents(
                "/proc/AnnotatedMemberSource/annotatedfield FIELD\n", basedir.toFile(), "target/classes/elements.lst");

        // incremental build, introduce new annotated element and assert elements.lst includes both the new and the old
        // elements
        JavaFile.builder(
                        "proc", //
                        TypeSpec.classBuilder("AnnotatedSource") //
                                .addAnnotation(annotation) //
                                .build()) //
                .build()
                .writeTo(src);
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ElementsListProcessor"));
        mojos.assertBuildOutputs(
                basedir.resolve("target/classes"),
                "proc/AnnotatedMemberSource.class",
                "proc/AnnotatedSource.class",
                "elements.lst");
        assertFileContents(
                "/proc/AnnotatedMemberSource/annotatedfield FIELD\n/proc/AnnotatedSource CLASS\n",
                basedir.toFile(),
                "target/classes/elements.lst");
    }

    @Test
    public void testNestedTypeReference() throws Exception {
        Path processor = compileAnnotationProcessor();
        Path basedir = resources.getBasedir().toPath();
        Path src = basedir.resolve("src/main/java");

        // initial build
        JavaFile.builder(
                        "proc", //
                        TypeSpec.classBuilder("Dependency") //
                                .addType(TypeSpec.classBuilder("Nested").build()) //
                                .build()) //
                .build()
                .writeTo(src);
        AnnotationSpec annotation =
                AnnotationSpec.builder(ClassName.get("processor", "Annotation")).build();
        JavaFile.builder(
                        "proc", //
                        TypeSpec.classBuilder("AnnotatedSource") //
                                .addField(FieldSpec.builder(ClassName.get("proc", "Dependency"), "annotatedfield")
                                        .addAnnotation(annotation)
                                        .build()) //
                                .build()) //
                .build()
                .writeTo(src);
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ElementsTypeMemberListProcessor"));
        mojos.assertBuildOutputs(
                basedir.resolve("target/classes"),
                "proc/AnnotatedSource.class",
                "proc/Dependency.class",
                "proc/Dependency$Nested.class",
                "elements.lst");
        assertFileContents("<init> CONSTRUCTOR\n", basedir.toFile(), "target/classes/elements.lst");

        // add new member to Dependency.Nested type, run incremental build
        JavaFile.builder(
                        "proc", //
                        TypeSpec.classBuilder("Dependency") //
                                .addType(TypeSpec.classBuilder("Nested") //
                                        .addField(FieldSpec.builder(String.class, "nestedTypeField")
                                                .build()) //
                                        .build()) //
                                .build()) //
                .build()
                .writeTo(src);
        processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ElementsTypeMemberListProcessor"));
        mojos.assertBuildOutputs(
                basedir.resolve("target/classes"),
                "proc/AnnotatedSource.class",
                "proc/Dependency.class",
                "proc/Dependency$Nested.class",
                "elements.lst");
        assertFileContents(
                "<init> CONSTRUCTOR\nnestedTypeField FIELD\n", basedir.toFile(), "target/classes/elements.lst");
    }
}
