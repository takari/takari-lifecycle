package io.takari.maven.plugins.compile;

import io.takari.maven.plugins.compiler.incremental.AbstractCompileMojo.Proc;
import io.takari.maven.plugins.compiler.incremental.ClasspathEntryDigester;
import io.takari.maven.plugins.compiler.incremental.ClasspathEntryIndex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class CompileTest extends AbstractCompileTest {

  private static final boolean isJava7;

  static {
    boolean isJava7x = true;
    try {
      Class.forName("java.nio.file.Files");
    } catch (Exception e) {
      isJava7x = false;
    }
    isJava7 = isJava7x;
  }

  public CompileTest(String compilerId, boolean fork) {
    super(compilerId, fork);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = compile("compile/basic");
    File classes = new File(basedir, "target/classes");
    mojos.assertBuildOutputs(classes, "basic/Basic.class");
    Assert.assertTrue(new File(classes, ClasspathEntryDigester.TYPE_INDEX_LOCATION).isFile());
    ClasspathEntryIndex index =
        new ClasspathEntryDigester().readIndex(classes, mojos.getStartTime());
    Assert.assertTrue(index.isPersistent());
    Assert.assertEquals(1, index.getIndex().get("basic.Basic").size());
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
      Assert.assertTrue(output.contains("parsing started"));
    } finally {
      System.setOut(origOut);
    }
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
    Xpp3Dom includes = new Xpp3Dom("excludes");
    includes.addChild(newParameter("exclude", "basic/Garbage.java"));
    File basedir = compile("compile/source-filtering", includes);

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
  public void testSpace() throws Exception {
    File basedir = compile("compile/spa ce");
    Assert.assertTrue(basedir.getAbsolutePath().contains(" "));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "space/Space.class");
  }

  @Test
  public void testProc_only() throws Exception {
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
    File basedir = procCompile("compile/proc", Proc.proc);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class", //
        "generated-sources/annotations/proc/AnotherGeneratedSource.java", //
        "classes/proc/AnotherGeneratedSource.class");
  }

  @Test
  public void testProc_annotationProcessors() throws Exception {
    Xpp3Dom processors = new Xpp3Dom("annotationProcessors");
    processors.addChild(newParameter("processor", "processor.Processor"));
    File basedir = procCompile("compile/proc", Proc.proc, processors);
    mojos.assertBuildOutputs(new File(basedir, "target"), //
        "classes/proc/Source.class", //
        "generated-sources/annotations/proc/GeneratedSource.java", //
        "classes/proc/GeneratedSource.class");
  }

  @Test
  public void testError() throws Exception {
    File basedir = resources.getBasedir("compile/error");
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException e) {
      Assert.assertEquals("1 error(s) encountered, see previous message(s) for details",
          e.getMessage());
    }
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos
        .assertMessages(basedir, "src/main/java/error/Error.java",
            "ERROR Error.java [4:11] cannot find symbol\n  symbol:   class Foo\n  location: class basic.Error");
  }

  @Test
  public void testSourceTargetVersion() throws Exception {
    Assume.assumeTrue(isJava7);
    File basedir = resources.getBasedir("compile/source-target-version");
    try {
      compile(basedir);
    } catch (MojoExecutionException e) {
      Assert.assertEquals("1 error(s) encountered, see previous message(s) for details",
          e.getMessage());
    }
    mojos.assertMessages(basedir, "src/main/java/version/RequiresJava7.java",
        "ERROR RequiresJava7.java [9:50] diamond operator is not supported in -source 1.6\n"
            + "  (use -source 7 or higher to enable diamond operator)");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), new String[0]);

    compile(basedir, newParameter("source", "1.7"));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "version/RequiresJava7.class");
  }
}
