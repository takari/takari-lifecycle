package io.takari.maven.plugins.compile;

import static io.takari.maven.plugins.compile.ClassfileMatchers.hasDebugLines;
import static io.takari.maven.plugins.compile.ClassfileMatchers.hasDebugSource;
import static io.takari.maven.plugins.compile.ClassfileMatchers.hasDebugVars;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

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
  public void testBasic_projectArtifactFile() throws Exception {
    File basedir = resources.getBasedir("compile/basic");
    MavenProject project = mojos.readMavenProject(basedir);
    mojos.compile(project);
    Assert.assertEquals(new File(basedir, "target/classes"), project.getArtifact().getFile());

    // no-change rebuild
    project = mojos.readMavenProject(basedir);
    mojos.compile(project);
    Assert.assertEquals(new File(basedir, "target/classes"), project.getArtifact().getFile());
  }

  @Test
  public void testBasic_debugInfo() throws Exception {
    File basedir;

    basedir = compile("compile/basic");
    assertThat(new File(basedir, "target/classes/basic/Basic.class"),
        allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));

    basedir = compile("compile/basic", newParameter("debug", "none"));
    assertThat(new File(basedir, "target/classes/basic/Basic.class"),
        allOf(not(hasDebugSource()), not(hasDebugLines()), not(hasDebugVars())));

    basedir = compile("compile/basic", newParameter("debug", "source"));
    assertThat(new File(basedir, "target/classes/basic/Basic.class"),
        allOf(hasDebugSource(), not(hasDebugLines()), not(hasDebugVars())));
    basedir = compile("compile/basic", newParameter("debug", "source,lines"));
    assertThat(new File(basedir, "target/classes/basic/Basic.class"),
        allOf(hasDebugSource(), hasDebugLines(), not(hasDebugVars())));
    basedir = compile("compile/basic", newParameter("debug", "source,lines,vars"));
    assertThat(new File(basedir, "target/classes/basic/Basic.class"),
        allOf(hasDebugSource(), hasDebugLines(), hasDebugVars()));
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
  public void testError() throws Exception {
    ErrorMessage expected = new ErrorMessage(compilerId);
    expected.setSnippets("jdt", "ERROR Error.java [4:11] Foo cannot be resolved to a type");
    expected.setSnippets("javac", "ERROR Error.java [4:11]", "cannot find symbol", "class Foo",
        "location", "class basic.Error");

    File basedir = resources.getBasedir("compile/error");
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException e) {
      Assert.assertEquals("1 error(s) encountered, see previous message(s) for details",
          e.getMessage());
    }
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos.assertMessage(basedir, "src/main/java/error/Error.java", expected);
  }

  @Test
  public void testSourceTargetVersion() throws Exception {
    Assume.assumeTrue(isJava7);

    ErrorMessage expected = new ErrorMessage(compilerId);
    expected.setSnippets("jdt",
        "ERROR RequiresJava7.java [9:40] '<>' operator is not allowed for source level below 1.7");
    expected.setSnippets("javac",
        "ERROR RequiresJava7.java [9:50] diamond operator is not supported in -source 1.6\n"
            + "  (use -source 7 or higher to enable diamond operator)");

    File basedir = resources.getBasedir("compile/source-target-version");
    try {
      compile(basedir);
    } catch (MojoExecutionException e) {
      Assert.assertEquals("1 error(s) encountered, see previous message(s) for details",
          e.getMessage());
    }
    mojos.assertMessage(basedir, "src/main/java/version/RequiresJava7.java", expected);
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), new String[0]);

    compile(basedir, newParameter("source", "1.7"));
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "version/RequiresJava7.class");
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
    mojos.assertMessageContains(new File(basedir, "src/main/java/encoding/ISO8859p5.java"),
        "\u043f\u043e\u0440\u0443\u0441\u0441\u043a\u0438"); // "inrussian" in UTF8 Russian
  }

  @Test
  public void testEmpty() throws Exception {
    File basedir = compile("compile/empty");
    mojos.assertBuildOutputs(basedir, new String[0]);
    Assert.assertFalse("outputDirectory was not created",
        new File(basedir, "target/classes").exists());
  }
}
