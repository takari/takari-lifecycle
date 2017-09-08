package io.takari.maven.plugins.compile;

import static io.takari.maven.testing.TestResources.assertFileContents;
import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.rm;
import static io.takari.maven.testing.TestResources.touch;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
import org.junit.runners.Parameterized.Parameters;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;
import io.takari.maven.plugins.compile.javac.CompilerJavac;
import io.takari.maven.plugins.compile.jdt.CompilerJdt;

public class AnnotationProcessingTest extends AbstractCompileTest {

  public AnnotationProcessingTest(String compilerId) {
    super(compilerId);
  }

  @Parameters(name = "{0}")
  public static Iterable<Object[]> compilers() {
    List<Object[]> compilers = new ArrayList<Object[]>();
    compilers.add(new Object[] {"javac"});
    compilers.add(new Object[] {"forked-javac"});
    compilers.add(new Object[] {"jdt"});
    return compilers;
  }

  private File procCompile(String projectName, Proc proc, Xpp3Dom... parameters) throws Exception, IOException {
    File basedir = resources.getBasedir(projectName);
    return procCompile(basedir, proc, parameters);
  }

  private File procCompile(File basedir, Proc proc, Xpp3Dom... parameters) throws Exception, IOException {
    File processor = compileAnnotationProcessor();
    return processAnnotations(basedir, proc, processor, parameters);
  }

  private File processAnnotations(File basedir, Proc proc, File processor, Xpp3Dom... parameters) throws Exception {
    MavenProject project = mojos.readMavenProject(basedir);
    processAnnotations(project, processor, proc, parameters);
    return basedir;
  }

  protected void processAnnotations(MavenProject project, File processor, Proc proc, Xpp3Dom... parameters) throws Exception {
    MavenSession session = mojos.newMavenSession(project);
    processAnnotations(session, project, "compile", processor, proc, parameters);
  }

  protected void processAnnotations(MavenSession session, MavenProject project, String goal, File processor, Proc proc, Xpp3Dom... parameters) throws Exception {
    MojoExecution execution = mojos.newMojoExecution(goal);

    addDependency(project, "processor", new File(processor, "target/classes"));

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

  private File compileAnnotationProcessor() throws Exception, IOException {
    File processor = compile("compile-proc/processor");
    cp(processor, "src/main/resources/META-INF/services/javax.annotation.processing.Processor", "target/classes/META-INF/services/javax.annotation.processing.Processor");
    return processor;
  }


  @Test
  public void testProc_only() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.only);
    mojos.assertBuildOutputs(new File(basedir, "target/generated-sources/annotations"), "proc/GeneratedSource.java", "proc/AnotherGeneratedSource.java");
  }

  @Test
  public void testProc_none() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.none);
    mojos.assertBuildOutputs(new File(basedir, "target"), "classes/proc/Source.class");
  }

  @Test
  public void testProc_proc() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.proc);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProc_incrementalTypeIndex() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");
    File src = new File(basedir, "src/main/java");

    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "proc/Source.class", "types.lst");
    assertFileContents("proc.Source\n", basedir, "target/classes/types.lst");

    // create new annotated source and run incremental build
    JavaFile.builder("proc", //
        TypeSpec.classBuilder("AnotherSource") //
            .addAnnotation(AnnotationSpec.builder(ClassName.get("processor", "Annotation")).build()) //
            .build()) //
        .build().writeTo(src);
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "proc/Source.class", "proc/AnotherSource.class", "types.lst");
    assertFileContents("proc.AnotherSource\nproc.Source\n", basedir, "target/classes/types.lst");

    // delete the new source and run incremental build
    rm(src, "proc/AnotherSource.java");
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "proc/Source.class", "types.lst");
    mojos.assertDeletedOutputs(new File(basedir, "target/classes"), "proc/AnotherSource.class");
    assertFileContents("proc.Source\n", basedir, "target/classes/types.lst");
  }

  @Test
  public void testProc_incrementalProcessorChange() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");
    processAnnotations(basedir, Proc.proc, processor);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");

    rm(processor, "target/classes/META-INF/services/javax.annotation.processing.Processor");
    mojos.flushClasspathCaches();
    processAnnotations(basedir, Proc.proc, processor);
    mojos.assertBuildOutputs(new File(basedir, "target"), "classes/proc/Source.class");
    mojos.assertDeletedOutputs(new File(basedir, "target"), //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProc_dummyOutput() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.proc, newProcessors("processor.Processor_dummyOutput"));
    mojos.assertBuildOutputs(new File(basedir, "target"), "classes/proc/Source.class");
  }

  @Test
  public void testProcTypeReference() throws Exception {
    File basedir = procCompile("compile-proc/proc-type-reference", Proc.proc);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/GeneratedSourceSubclass.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProc_createResource() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.proc, newProcessors("processor.ProcessorCreateResource"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
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
    File basedir = procCompile("compile-proc/proc", Proc.proc, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
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
    File basedir = procCompile("compile-proc/proc-type-reference", Proc.proc, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
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
    File basedir = procCompile("compile-proc/proc-type-reference", Proc.proc, processors);

    mojos.assertBuildOutputs(new File(basedir, "target"), //
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
    File basedir = procCompile("compile-proc/proc-type-reference", Proc.proc, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "classes/proc/GeneratedSourceSubclass.class");

    touch(basedir, "src/main/java/proc/Source.java");

    procCompile(basedir, Proc.proc, processors);

    String[] expectedOuput;
    if (CompilerJdt.ID.equals(compilerId)) {
      // incremental in jdt compile means that GeneratedSourceSubclass sees no
      // structural change and doesn't need to be recompiled.
      expectedOuput = new String[] {//
          "classes/proc/Source.class", //
          "generated-sources/annotations/proc/GeneratedSource.java", //
          "classes/proc/GeneratedSource.class"};
    } else {
      expectedOuput = new String[] { //
          // everything is recompiled by javac
          "classes/proc/Source.class", //
          "generated-sources/annotations/proc/GeneratedSource.java", //
          "classes/proc/GeneratedSource.class", //
          "classes/proc/GeneratedSourceSubclass.class"};
    }
    mojos.assertBuildOutputs(new File(basedir, "target"), expectedOuput);
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
    File basedir = procCompile("compile-proc/proc-type-reference", Proc.proc, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "classes/proc/GeneratedSourceSubclass.class");

    touch(basedir, "src/main/java/proc/Source.java");

    procCompile(basedir, Proc.proc, processors);

    // okay this is a little wonky. Since source was recompiled, we delete it's
    // apt generated type, which is referenced by GeneratedSourceSubclass, once it's
    // deleted we then generated the GeneratedSource, compile it, and then have to
    // recompile GeneratedSourceSubclass, hence it appears in this test and not
    // testProcRef_annotationProcessors_recompile's list of output for jdt.
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "classes/proc/GeneratedSourceSubclass.class");
  }


  @Test
  public void testProc_processorErrorMessage() throws Exception {
    Xpp3Dom processors = newProcessors("processor.ErrorMessageProcessor");
    File basedir = resources.getBasedir("compile-proc/proc");
    try {
      procCompile(basedir, Proc.only, processors);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertBuildOutputs(new File(basedir, "target"), new String[0]);

    ErrorMessage expected = new ErrorMessage(compilerId);
    expected.setSnippets("jdt", "ERROR Source.java [6:14] test error message"); // TODO why 14?
    expected.setSnippets("javac", "ERROR Source.java [6:8] test error message");
    mojos.assertMessage(basedir, "src/main/java/proc/Source.java", expected);

    Collection<String> pomMessages = mojos.getBuildContextLog().getMessages(new File(basedir, "pom.xml"));
    Assert.assertEquals(3, pomMessages.size());
    // TODO assert actual messages are as expected
  }

  @Test
  public void testProc_messages() throws Exception {
    ErrorMessage expected = new ErrorMessage(compilerId);
    expected.setSnippets("javac", "ERROR BrokenSource.java [2:29]", "cannot find symbol");
    expected.setSnippets("jdt", "ERROR BrokenSource.java [2:29]", "cannot be resolved to a type");

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");

    String[] outputs;
    if (CompilerJdt.ID.equals(compilerId)) {
      outputs = new String[] {"classes/proc/Source.class" //
          , "generated-sources/annotations/proc/BrokenSource.java"};
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
    mojos.assertBuildOutputs(new File(basedir, "target"), outputs);
    assertProcMessage(basedir, "target/generated-sources/annotations/proc/BrokenSource.java", expected);

    // no change rebuild should produce the same messages
    try {
      processAnnotations(basedir, Proc.proc, processor, processors);
      Assert.fail();
    } catch (MojoExecutionException e) {
      // expected
    }
    mojos.assertCarriedOverOutputs(new File(basedir, "target"), outputs);
    assertProcMessage(basedir, "target/generated-sources/annotations/proc/BrokenSource.java", expected);
  }

  private void assertProcMessage(File basedir, String path, ErrorMessage expected) throws Exception {
    // javac reports the same compilation error twice when Proc.proc
    Set<String> messages = new HashSet<String>(mojos.getBuildContextLog().getMessages(new File(basedir, path)));
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
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");

    processAnnotations(basedir, Proc.proc, processor);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");

    // remove annotation
    cp(basedir, "src/main/java/proc/Source.java-remove-annotation", "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.proc, processor);
    mojos.assertDeletedOutputs(new File(basedir, "target"), //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProc_incrementalDeleteLastAnnotatedSource() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");

    Xpp3Dom processors = newProcessors("processor.Processor");

    // initial compilation
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    // no-change rebuild
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertCarriedOverOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    // remove annotated class
    rm(basedir, "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertDeletedOutputs(new File(basedir, "target"), //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/Source.class", //
        "classes/proc/GeneratedSource.class");
  }

  @Test
  public void testProc_nonIncrementalProcessor_onlyEX_deleteSource() throws Exception {
    File processor = compileAnnotationProcessor();

    File basedir = resources.getBasedir("compile-proc/proc");
    File target = new File(basedir, "target");

    Xpp3Dom processors = newProcessors("processor.NonIncrementalProcessor");

    processAnnotations(basedir, Proc.only, processor, processors);
    mojos.assertBuildOutputs(target, //
        "generated-sources/annotations/proc/NonIncrementalSource.java");

    rm(basedir, "src/main/java/proc/Source.java");

    processAnnotations(basedir, Proc.only, processor, processors);
    mojos.assertDeletedOutputs(target, //
        "generated-sources/annotations/proc/NonIncrementalSource.java");
  }

  @Test
  public void testProc_projectSourceRoots() throws Exception {
    File processor = compileAnnotationProcessor();

    File basedir = resources.getBasedir("compile-proc/proc");
    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "processor", new File(processor, "target/classes"));

    mojos.compile(project, newParameter("proc", Proc.proc.name()), newProcessors("processor.Processor"));

    Assert.assertTrue(project.getCompileSourceRoots().contains(new File(basedir, "target/generated-sources/annotations").getAbsolutePath()));

    // TODO testCompile
  }

  @Test
  public void testIncrementalDelete() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-incremental-delete");

    Xpp3Dom processors = newProcessors("processor.Processor");

    // initial compilation
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Keep.class", //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    // no-change rebuild
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertCarriedOverOutputs(new File(basedir, "target"), //
        "classes/proc/Keep.class", //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    // remove annotated source
    rm(basedir, "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.proc, processor, processors);
    mojos.assertDeletedOutputs(new File(basedir, "target"), //
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

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-incremental-move");
    File moduleA = new File(basedir, "module-a");
    File moduleB = new File(basedir, "module-b");

    Xpp3Dom processors = newProcessors("processor.Processor");

    mojos.compile(moduleB);
    MavenProject projectA = mojos.readMavenProject(moduleA);
    addDependency(projectA, "module-b", new File(moduleB, "target/classes"));
    processAnnotations(projectA, processor, Proc.proc, processors);
    mojos.assertBuildOutputs(new File(moduleA, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    mojos.flushClasspathCaches();

    // move generated source to module-b/src/main/java
    cp(moduleB, "src/main/java/proc/GeneratedSource.java-moved", "src/main/java/proc/GeneratedSource.java");
    cp(moduleA, "src/main/java/modulea/ModuleA.java-new", "src/main/java/modulea/ModuleA.java");
    cp(moduleA, "src/main/java/proc/Source.java-remove-annotation", "src/main/java/proc/Source.java");
    mojos.compile(moduleB);
    mojos.assertBuildOutputs(moduleB, "target/classes/proc/GeneratedSource.class");
    projectA = mojos.readMavenProject(moduleA);
    addDependency(projectA, "module-b", new File(moduleB, "target/classes"));
    processAnnotations(projectA, processor, Proc.proc, processors);
    mojos.assertBuildOutputs(new File(moduleA, "target"), //
        "classes/proc/Source.class", "classes/modulea/ModuleA.class");
    mojos.assertDeletedOutputs(new File(moduleA, "target"), //
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
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/require-proc");
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

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/require-proc");
    processAnnotations(basedir, null, processor, new Xpp3Dom("processorpath"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class");
  }

  @Test
  public void testRequireProc_processorpath() throws Exception {
    MavenProject annotations = mojos.readMavenProject(resources.getBasedir("compile-proc/processorpath-annotation"));
    mojos.compile(annotations);

    MavenProject processor = mojos.readMavenProject(resources.getBasedir("compile-proc/processorpath-processor"));
    mojos.newDependency(new File(annotations.getBasedir(), "target/classes")).setArtifactId("annotations").addTo(processor);
    mojos.compile(processor);
    cp(processor.getBasedir(), "src/main/resources/META-INF/services/javax.annotation.processing.Processor", "target/classes/META-INF/services/javax.annotation.processing.Processor");

    File basedir = resources.getBasedir("compile-proc/proc");
    MavenProject project = mojos.readMavenProject(basedir);
    mojos.newDependency(new File(annotations.getBasedir(), "target/classes")).setArtifactId("annotations").addTo(project);

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

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-incremental-recompile");

    Xpp3Dom processors = newProcessors("processor.ProcessorSiblingBody");
    Xpp3Dom options = new Xpp3Dom("annotationProcessorOptions");
    options.addChild(newParameter("basedir", new File(basedir, "src/main/java").getCanonicalPath()));

    processAnnotations(basedir, Proc.proc, processor, processors, options);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/Client.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");

    cp(basedir, "src/main/java/proc/GeneratedSource.body-changed", "src/main/java/proc/GeneratedSource.body");
    touch(basedir, "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.proc, processor, processors, options);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/Client.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");
  }

  @Test
  public void testProc_processorLastRound() throws Exception {
    Xpp3Dom processors = newProcessors("processor.ProcessorLastRound");
    File basedir = procCompile("compile-proc/proc", Proc.only, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/types.lst");

    assertFileContents("proc.Source\n", basedir, "target/classes/types.lst");
  }

  @Test
  public void testIncremental_proc_only() throws Exception {
    // the point of this test is to assert that changes to annotations trigger affected sources reprocessing when proc=only
    // note sourcepath=disable, otherwise proc:only is all-or-nothing

    // TODO this test likely becomes redundant when annotation processing is always all-or-nothing

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-incremental-proconly");
    File generatedSources = new File(basedir, "target/generated-sources/annotations");

    Xpp3Dom processors = newProcessors("processor.Processor");

    compile(basedir, processor, "compile-only");
    processAnnotations(basedir, Proc.only, processor, processors, newParameter("sourcepath", "disable"));
    mojos.assertBuildOutputs(generatedSources, "proc/GeneratedConcrete.java", "proc/GeneratedAbstract.java", "proc/GeneratedAnother.java");

    cp(basedir, "src/main/java/proc/Abstract.java-remove-annotation", "src/main/java/proc/Abstract.java");
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

  private void compile(File basedir, File processor, String executionId) throws Exception {
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = mojos.newMojoExecution();
    MojoExecution cloned = new MojoExecution(execution.getMojoDescriptor(), executionId, null);
    cloned.setConfiguration(execution.getConfiguration());
    execution.getConfiguration().addChild(newParameter("proc", Proc.none.name()));
    addDependency(project, "processor", new File(processor, "target/classes"));
    mojos.executeMojo(session, project, cloned);
  }

  @Test
  public void testMutliround_procOnly() throws Exception {
    File basedir = procCompile("compile-proc/multiround", Proc.only);
    File generatedSources = new File(basedir, "target/generated-sources/annotations");

    mojos.assertMessages(basedir, "src/main/java/multiround/Source.java", new String[] {});
    mojos.assertBuildOutputs(generatedSources, "multiround/GeneratedSource.java", "multiround/AnotherGeneratedSource.java");
  }

  @Test
  public void testMultiround_processorLastRound() throws Exception {
    // processor.ProcessorLastRound creates well-known resource during last round
    // the point of this test is to assert this works during incremental build
    // when compiler may be invoked several times to compile indirectly affected sources

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/multiround-type-reference");

    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/AnotherSource.class", //
        "classes/types.lst");

    cp(basedir, "src/main/java/proc/Source.java-changed", "src/main/java/proc/Source.java");
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProcessorLastRound"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/AnotherSource.class", //
        "classes/types.lst");
  }

  @Test
  public void testLastRound_typeIndex() throws Exception {
    // apparently javac can't resolve "forward" references to types generated during apt last round
    Assume.assumeTrue(CompilerJdt.ID.equals(compilerId));

    Xpp3Dom processors = newProcessors("processor.ProcessorLastRound_typeIndex");
    File basedir = procCompile("compile-proc/multiround-type-index", Proc.proc, processors);
    File target = new File(basedir, "target");
    mojos.assertBuildOutputs(target, //
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
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/reprocess");
    File target = new File(basedir, "target");

    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProessorValue"));
    mojos.assertBuildOutputs(target, //
        "classes/reprocess/Annotated.class", //
        "classes/reprocess/Annotated.value", //
        "classes/reprocess/SimpleA.class", //
        "classes/reprocess/SimpleB.class");

    Assert.assertEquals("1", FileUtils.fileRead(new File(target, "classes/reprocess/Annotated.value")));

    cp(basedir, "src/main/java/reprocess/SimpleA.java-changed", "src/main/java/reprocess/SimpleA.java");
    touch(new File(basedir, "src/main/java/reprocess/Annotated.java"));

    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ProessorValue"));
    mojos.assertBuildOutputs(target, //
        "classes/reprocess/Annotated.class", //
        "classes/reprocess/Annotated.value", //
        "classes/reprocess/SimpleA.class", //
        "classes/reprocess/SimpleB.class");

    Assert.assertEquals("10", FileUtils.fileRead(new File(target, "classes/reprocess/Annotated.value")));
  }


  @Test
  public void testProc_nonIncrementalProcessor() throws Exception {
    Assume.assumeTrue(CompilerJdt.ID.equals(compilerId));

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");
    File target = new File(basedir, "target");

    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.NonIncrementalProcessor"));
    mojos.assertBuildOutputs(target, //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/NonIncrementalSource.java", //
        "classes/proc/NonIncrementalSource.class");

    FileUtils.deleteDirectory(target);
    processAnnotations(basedir, Proc.only, processor, newProcessors("processor.NonIncrementalProcessor"));
    mojos.assertBuildOutputs(target, //
        "generated-sources/annotations/proc/NonIncrementalSource.java");

    FileUtils.deleteDirectory(target);
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.NonIncrementalProcessor"));
    mojos.assertBuildOutputs(target, //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/NonIncrementalSource.java", //
        "classes/proc/NonIncrementalSource.class");

    FileUtils.deleteDirectory(target);
    processAnnotations(basedir, Proc.only, processor, newProcessors("processor.NonIncrementalProcessor"));
    mojos.assertBuildOutputs(target, //
        "generated-sources/annotations/proc/NonIncrementalSource.java");
  }

  @Test
  public void testProc_processorpath() throws Exception {
    MavenProject annotations = mojos.readMavenProject(resources.getBasedir("compile-proc/processorpath-annotation"));
    mojos.compile(annotations);

    MavenProject processor = mojos.readMavenProject(resources.getBasedir("compile-proc/processorpath-processor"));
    mojos.newDependency(new File(annotations.getBasedir(), "target/classes")).setArtifactId("annotations").addTo(processor);
    mojos.compile(processor);
    cp(processor.getBasedir(), "src/main/resources/META-INF/services/javax.annotation.processing.Processor", "target/classes/META-INF/services/javax.annotation.processing.Processor");

    File basedir = resources.getBasedir("compile-proc/proc");
    MavenProject project = mojos.readMavenProject(basedir);
    mojos.newDependency(new File(annotations.getBasedir(), "target/classes")).setArtifactId("annotations").addTo(project);

    MavenSession session = mojos.newMavenSession(project);
    SimpleReactorReader.builder() //
        .addProjects(annotations, processor, project) //
        .toSession(session.getRepositorySession());

    session.setProjects(Arrays.asList(annotations, processor, project));
    session.setCurrentProject(project);
    Xpp3Dom processorpath = new Xpp3Dom("processorpath");
    processorpath.addChild(newProcessorpathEntry(processor));
    mojos.compile(session, project, newParameter("proc", "proc"), processorpath);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");
  }

  @Test
  public void testProc_emptyProcessorPath() throws Exception {
    File basedir = procCompile("compile-proc/proc", Proc.proc, new Xpp3Dom("processorpath"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class");
  }

  @Test
  public void testProc_incrementalTypeJump() throws Exception {
    // assert incremental behaviour when processor uses Elements to access non-annotated types
    // jdt is expected to run annotation processor if referenced non-annotated types change
    // (obviously still need to run apt when annotated types change, but that is tested elsewhere)

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc");
    File src = new File(basedir, "src/main/java");

    // processor references non-existing type proc.SourceJump
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.TypeJumpingProcessor"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class");

    // create proc.SourceJump, assert apt ran as expected
    JavaFile.builder("proc", TypeSpec.classBuilder("SourceJump").build()).build().writeTo(src);
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.TypeJumpingProcessor"));
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc.SourceJump", // the generated resource
        "classes/proc/Source.class", //
        "classes/proc/SourceJump.class" //
    );

    // create proc.Unrelated, assert apt did NOT run
    JavaFile.builder("proc", TypeSpec.classBuilder("Unrelated").build()).build().writeTo(src);
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.TypeJumpingProcessor"));
    if (CompilerJdt.ID.equals(compilerId)) {
      // jdt is supposed to compile the new type but carry-over everything else
      mojos.assertBuildOutputs(new File(basedir, "target"), //
          "classes/proc/Unrelated.class");
      mojos.assertDeletedOutputs(new File(basedir, "target"), new String[0]);
      mojos.assertCarriedOverOutputs(new File(basedir, "target"), //
          "classes/proc.SourceJump", // the generated resource
          "classes/proc/Source.class", //
          "classes/proc/SourceJump.class" //
      );
    } else {
      // javac recompiles everything
      mojos.assertBuildOutputs(new File(basedir, "target"), //
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
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-sourcepath");

    File dependencyBasedir = new File(basedir, "dependency");
    File projectBasedir = new File(basedir, "project");

    Xpp3Dom processors = newProcessors("processor.Processor");
    Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

    MavenProject dependency = mojos.readMavenProject(dependencyBasedir);
    MavenProject project = mojos.readMavenProject(projectBasedir);

    mojos.newDependency(new File(dependencyBasedir, "target/classes")) //
        .setGroupId(dependency.getGroupId()) //
        .setArtifactId(dependency.getArtifactId()) //
        .setVersion(dependency.getVersion()) //
        .addTo(project);

    MavenSession session = mojos.newMavenSession(project);
    session.setProjects(Arrays.asList(project, dependency));

    processAnnotations(session, project, "compile", processor, Proc.only, processors, sourcepath);
    mojos.assertBuildOutputs(new File(projectBasedir, "target"), //
        "generated-sources/annotations/sourcepath/project/GeneratedSource.java" //
    );
  }

  @Test
  public void testSourcepathDependency_incremental() throws Exception {
    // the point of this test is assert that changes to sourcepath files are expected to trigger reprocessing of affected sources

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-sourcepath");

    File dependencyBasedir = new File(basedir, "dependency");
    File projectBasedir = new File(basedir, "project");

    Xpp3Dom processors = newProcessors("processor.Processor");
    Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

    MavenProject dependency = mojos.readMavenProject(dependencyBasedir);
    MavenProject project = mojos.readMavenProject(projectBasedir);

    mojos.newDependency(new File(dependencyBasedir, "target/classes")) //
        .setGroupId(dependency.getGroupId()) //
        .setArtifactId(dependency.getArtifactId()) //
        .setVersion(dependency.getVersion()) //
        .addTo(project);

    MavenSession session = mojos.newMavenSession(project);
    session.setProjects(Arrays.asList(project, dependency));

    processAnnotations(session, project, "compile", processor, Proc.only, processors, sourcepath);
    mojos.assertBuildOutputs(new File(projectBasedir, "target"), //
        "generated-sources/annotations/sourcepath/project/GeneratedSource.java" //
    );

    // second, incremental, compilation with one of sourcepath files removed

    rm(dependencyBasedir, "src/main/java/sourcepath/dependency/SourcepathDependency.java");
    mojos.flushClasspathCaches();

    dependency = mojos.readMavenProject(dependencyBasedir);
    project = mojos.readMavenProject(projectBasedir);

    mojos.newDependency(new File(dependencyBasedir, "target/classes")) //
        .setGroupId(dependency.getGroupId()) //
        .setArtifactId(dependency.getArtifactId()) //
        .setVersion(dependency.getVersion()) //
        .addTo(project);

    session = mojos.newMavenSession(project);
    session.setProjects(Arrays.asList(project, dependency));
    try {
      processAnnotations(session, project, "compile", processor, Proc.only, processors, sourcepath);
    } catch (MojoExecutionException expected) {}
    ErrorMessage message = new ErrorMessage(compilerId);
    message.setSnippets(CompilerJdt.ID, "sourcepath.dependency.SourcepathDependency cannot be resolved to a type");
    message.setSnippets(CompilerJavac.ID, "package sourcepath.dependency does not exist");
    mojos.assertMessage(projectBasedir, "src/main/java/sourcepath/project/Source.java", message);
    // oddly enough, both jdt and javac generate GeneratedSource despite the error
  }

  @Test
  public void testSourcepathDependency_classifiedDependency() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-sourcepath");

    File dependencyBasedir = new File(basedir, "dependency");
    File projectBasedir = new File(basedir, "project");

    Xpp3Dom processors = newProcessors("processor.Processor");
    Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

    MavenProject dependency = mojos.readMavenProject(dependencyBasedir);
    MavenProject project = mojos.readMavenProject(projectBasedir);

    mojos.newDependency(new File(dependencyBasedir, "target/classes")) //
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
      Assert.assertTrue(expected.getMessage().contains(dependency.getGroupId() + ":" + dependency.getArtifactId()));
    }
  }

  @Test
  public void testSourcepathIncludes() throws Exception {
    Xpp3Dom includes = new Xpp3Dom("includes");
    includes.addChild(newParameter("include", "sourcepath/project/*.java"));
    Xpp3Dom processors = newProcessors("processor.Processor");
    Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");
    File basedir = procCompile("compile-proc/proc-sourcepath-includes", Proc.only, includes, processors, sourcepath);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "generated-sources/annotations/sourcepath/project/GeneratedSource.java" //
    );
  }

  @Test
  public void testSourcepathDependency_testCompile() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-sourcepath");

    File dependencyBasedir = new File(basedir, "dependency");
    File projectBasedir = new File(basedir, "project");

    Xpp3Dom processors = newProcessors("processor.Processor");
    Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

    MavenProject dependency = mojos.readMavenProject(dependencyBasedir);
    MavenProject project = mojos.readMavenProject(projectBasedir);

    mojos.newDependency(new File(dependencyBasedir, "target/classes")) //
        .setGroupId(dependency.getGroupId()) //
        .setArtifactId(dependency.getArtifactId()) //
        .setVersion(dependency.getVersion()) //
        .addTo(project);

    mojos.newDependency(new File(dependencyBasedir, "target/test-classes")) //
        .setGroupId(dependency.getGroupId()) //
        .setArtifactId(dependency.getArtifactId()) //
        .setType("test-jar") //
        .setVersion(dependency.getVersion()) //
        .addTo(project);

    MavenSession session = mojos.newMavenSession(project);
    session.setProjects(Arrays.asList(project, dependency));

    processAnnotations(session, project, "testCompile", processor, Proc.only, processors, sourcepath);
    mojos.assertBuildOutputs(new File(projectBasedir, "target"), //
        "generated-test-sources/test-annotations/sourcepath/project/test/GeneratedSourceTest.java" //
    );
  }

  @Test
  public void testSourcepath_classpathVisibility() throws Exception {
    Assume.assumeTrue(CompilerJdt.ID.equals(compilerId));

    File basedir = resources.getBasedir();
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
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-sourcepath-secondarytype");

    File dependencyBasedir = new File(basedir, "dependency");
    File projectBasedir = new File(basedir, "project");

    Xpp3Dom processors = newProcessors("processor.Processor");
    Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");

    MavenProject dependency = mojos.readMavenProject(dependencyBasedir);
    MavenProject project = mojos.readMavenProject(projectBasedir);

    mojos.newDependency(new File(dependencyBasedir, "target/classes")) //
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

    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir("compile-proc/proc-sourcepath-cache");

    Xpp3Dom processors = newProcessors("processor.Processor");
    Xpp3Dom sourcepath = newParameter("sourcepath", "reactorDependencies");
    Xpp3Dom procOnly = newParameter("proc", Proc.only.name());

    // setting up project-a
    File projectaBasedir = new File(basedir, "project-a");
    MavenProject projecta = mojos.readMavenProject(projectaBasedir);
    addDependency(projecta, "processor", new File(processor, "target/classes"));
    projecta.addCompileSourceRoot(new File(projectaBasedir, "target/generated-sources/annotations").getCanonicalPath());

    // setting up project-b
    File projectbBasedir = new File(basedir, "project-b");
    MavenProject projectb = mojos.readMavenProject(projectbBasedir);
    addDependency(projectb, "processor", new File(processor, "target/classes"));
    mojos.newDependency(new File(projectaBasedir, "target/classes")) //
        .setGroupId(projecta.getGroupId()) //
        .setArtifactId(projecta.getArtifactId()) //
        .setVersion(projecta.getVersion()) //
        .addTo(projectb);

    MavenSession session = mojos.newMavenSession(projecta);
    session.setProjects(Arrays.asList(projecta, projecta));

    // process annotations in project-a
    mojos.executeMojo(session, projecta, "compile", procOnly, processors, sourcepath);
    mojos.assertBuildOutputs(new File(projectaBasedir, "target"), //
        "generated-sources/annotations/sourcepath/projecta/GeneratedSourceA.java" //
    );

    // process annotations in project-b, requires access to project-a generated sources
    session.setCurrentProject(projectb);
    mojos.executeMojo(session, projectb, "compile", procOnly, processors, sourcepath);
    mojos.assertBuildOutputs(new File(projectbBasedir, "target"), //
        "generated-sources/annotations/sourcepath/projectb/GeneratedSourceB.java" //
    );
  }

  @Test
  public void testAnnotatedMember_incrementalProcessingTrigger() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir();
    File src = new File(basedir, "src/main/java");

    // initial build, writes annotated field path is written to elements.lst file
    AnnotationSpec annotation = AnnotationSpec.builder(ClassName.get("processor", "Annotation")).build();
    JavaFile.builder("proc", //
        TypeSpec.classBuilder("AnnotatedMemberSource") //
            .addField(FieldSpec.builder(String.class, "annotatedfield").addAnnotation(annotation).build()) //
            .build()) //
        .build().writeTo(src);
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ElementsListProcessor"));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "proc/AnnotatedMemberSource.class", "elements.lst");
    assertFileContents("/proc/AnnotatedMemberSource/annotatedfield FIELD\n", basedir, "target/classes/elements.lst");

    // incremental build, introduce new annotated element and assert elements.lst includes both the new and the old elements
    JavaFile.builder("proc", //
        TypeSpec.classBuilder("AnnotatedSource") //
            .addAnnotation(annotation) //
            .build()) //
        .build().writeTo(src);
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ElementsListProcessor"));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "proc/AnnotatedMemberSource.class", "proc/AnnotatedSource.class", "elements.lst");
    assertFileContents("/proc/AnnotatedMemberSource/annotatedfield FIELD\n/proc/AnnotatedSource CLASS\n", basedir, "target/classes/elements.lst");
  }

  @Test
  public void testNestedTypeReference() throws Exception {
    File processor = compileAnnotationProcessor();
    File basedir = resources.getBasedir();
    File src = new File(basedir, "src/main/java");

    // initial build
    JavaFile.builder("proc", //
        TypeSpec.classBuilder("Dependency") //
            .addType(TypeSpec.classBuilder("Nested").build()) //
            .build()) //
        .build().writeTo(src);
    AnnotationSpec annotation = AnnotationSpec.builder(ClassName.get("processor", "Annotation")).build();
    JavaFile.builder("proc", //
        TypeSpec.classBuilder("AnnotatedSource") //
            .addField(FieldSpec.builder(ClassName.get("proc", "Dependency"), "annotatedfield").addAnnotation(annotation).build()) //
            .build()) //
        .build().writeTo(src);
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ElementsTypeMemberListProcessor"));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "proc/AnnotatedSource.class", "proc/Dependency.class", "proc/Dependency$Nested.class", "elements.lst");
    assertFileContents("<init> CONSTRUCTOR\n", basedir, "target/classes/elements.lst");

    // add new member to Dependency.Nested type, run incremental build
    JavaFile.builder("proc", //
        TypeSpec.classBuilder("Dependency") //
            .addType(TypeSpec.classBuilder("Nested") //
                .addField(FieldSpec.builder(String.class, "nestedTypeField").build()) //
                .build()) //
            .build()) //
        .build().writeTo(src);
    processAnnotations(basedir, Proc.proc, processor, newProcessors("processor.ElementsTypeMemberListProcessor"));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "proc/AnnotatedSource.class", "proc/Dependency.class", "proc/Dependency$Nested.class", "elements.lst");
    assertFileContents("<init> CONSTRUCTOR\nnestedTypeField FIELD\n", basedir, "target/classes/elements.lst");
  }
}
