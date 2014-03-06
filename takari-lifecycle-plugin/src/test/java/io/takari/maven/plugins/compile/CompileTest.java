package io.takari.maven.plugins.compile;

import io.takari.maven.plugins.compiler.incremental.AbstractCompileMojo.Proc;

import java.io.File;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;

public class CompileTest extends AbstractCompileTest {

  public CompileTest(String compilerId, boolean fork) {
    super(compilerId, fork);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = compile("compile/basic");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }

  @Test
  public void testIncludes() throws Exception {
    File basedir = compile("compile/includes");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }

  @Test
  public void testExcludes() throws Exception {
    File basedir = compile("compile/excludes");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }

  @Test
  public void testClasspath() throws Exception {
    File dependency = new File(compile("compile/basic"), "target/classes");

    File basedir = resources.getBasedir("compile/classpath");
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = newMojoExecution();

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
  public void testProcIncludes() throws Exception {
    File basedir = procCompile("compile/proc-includes", null);
    mojos.assertBuildOutputs(new File(basedir, "target/generated-sources/annotations"),
        "proc/GeneratedSource.java");
  }

  @Test
  public void testProcExcludes() throws Exception {
    File basedir = procCompile("compile/proc-includes", null);
    mojos.assertBuildOutputs(new File(basedir, "target/generated-sources/annotations"),
        "proc/GeneratedSource.java");
  }

  @Test
  public void testProc_only() throws Exception {
    File basedir = procCompile("compile/proc", Proc.only);
    mojos.assertBuildOutputs(new File(basedir, "target/generated-sources/annotations"),
        "proc/GeneratedSource.java");
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
    mojos.assertBuildOutputs(new File(basedir, "target"), "classes/proc/Source.class",
        "generated-sources/annotations/proc/GeneratedSource.java",
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
}
