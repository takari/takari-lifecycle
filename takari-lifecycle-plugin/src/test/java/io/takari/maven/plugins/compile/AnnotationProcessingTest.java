package io.takari.maven.plugins.compile;

import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class AnnotationProcessingTest extends AbstractCompileTest {

  public AnnotationProcessingTest(String compilerId) {
    super(compilerId);
  }

  @Parameters(name = "{0}")
  public static Iterable<Object[]> compilers() {
    List<Object[]> compilers = new ArrayList<Object[]>();
    if (isJava7) {
      // in-process annotation processing is not supported on java 6
      compilers.add(new Object[] {"javac"});
    }
    compilers.add(new Object[] {"forked-javac"});
    // compilers.add(new Object[] {"jdt"});
    return compilers;
  }


  @Test
  public void testProc_only() throws Exception {
    Assume.assumeTrue(isJava7 || !"javac".equals(compilerId));

    File basedir = procCompile("compile/proc", Proc.only);
    mojos.assertBuildOutputs(new File(basedir, "target/generated-sources/annotations"),
        "proc/GeneratedSource.java", "proc/AnotherGeneratedSource.java");
  }

  @Test
  public void testProc_default() throws Exception {
    File basedir = procCompile("compile/proc", null);
    mojos.assertBuildOutputs(new File(basedir, "target"), "classes/proc/Source.class");
  }

  @Test
  public void testProc_none() throws Exception {
    File basedir = procCompile("compile/proc", Proc.none);
    mojos.assertBuildOutputs(new File(basedir, "target"), "classes/proc/Source.class");
  }

  @Test
  public void testProc_proc() throws Exception {
    Assume.assumeTrue(isJava7 || !"javac".equals(compilerId));

    File basedir = procCompile("compile/proc", Proc.proc);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProcTypeReference() throws Exception {
    Assume.assumeTrue(isJava7 || !"javac".equals(compilerId));

    File basedir = procCompile("compile/proc-type-reference", Proc.proc);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "classes/proc/GeneratedSourceSubclass.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProc_annotationProcessors() throws Exception {
    Assume.assumeTrue(isJava7 || !"javac".equals(compilerId));

    Xpp3Dom processors = new Xpp3Dom("annotationProcessors");
    processors.addChild(newParameter("processor", "processor.Processor"));
    File basedir = procCompile("compile/proc", Proc.proc, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");
  }

}
