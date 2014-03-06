package io.takari.maven.plugins.compile;

import static org.apache.maven.plugin.testing.resources.TestResources.cp;
import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompileTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  private final String compilerId;

  private final boolean fork;

  public CompileTest(String compilerId, boolean fork) {
    this.compilerId = compilerId;
    this.fork = fork;
  }

  @Parameters
  public static Iterable<Object[]> compilers() {
    return Arrays.asList( //
        new Object[] {"javac", Boolean.FALSE}, //
        new Object[] {"javac", Boolean.TRUE} //
        );
  }

  private File compile(String name) throws Exception {
    File basedir = resources.getBasedir(name);
    compile(basedir);
    return basedir;
  }

  private void compile(File basedir) throws Exception {
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = newMojoExecution();

    mojos.executeMojo(session, project, execution);
  }

  private MojoExecution newMojoExecution() {
    MojoExecution execution = mojos.newMojoExecution("compile-incremental");

    Xpp3Dom configuration = execution.getConfiguration();
    add(configuration, "fork", Boolean.toString(fork));
    return execution;
  }

  private void addDependency(MavenProject project, String artifactId, File file) throws Exception {
    ArtifactHandler handler = mojos.getContainer().lookup(ArtifactHandler.class, "jar");
    DefaultArtifact artifact =
        new DefaultArtifact("test", artifactId, "1.0", Artifact.SCOPE_COMPILE, "jar", null, handler);
    artifact.setFile(file);
    Set<Artifact> artifacts = project.getArtifacts();
    artifacts.add(artifact);
    project.setArtifacts(artifacts);
  }

  private void add(Xpp3Dom configuration, String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    configuration.addChild(child);
  }

  private File procCompile(String projectName) throws Exception, IOException {
    File processor = compile("compile/processor");
    cp(processor, "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
        "target/classes/META-INF/services/javax.annotation.processing.Processor");

    File basedir = resources.getBasedir(projectName);
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = newMojoExecution();

    addDependency(project, "processor", new File(processor, "target/classes"));

    mojos.executeMojo(session, project, execution);
    return basedir;
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
    File basedir = procCompile("compile/proc-includes");
    mojos.assertBuildOutputs(new File(basedir, "target/generated-sources/annotations"),
        "proc/GeneratedSource.java");
  }

  @Test
  public void testProcExcludes() throws Exception {
    File basedir = procCompile("compile/proc-includes");
    mojos.assertBuildOutputs(new File(basedir, "target/generated-sources/annotations"),
        "proc/GeneratedSource.java");
  }

  @Test
  public void testProcOnly() throws Exception {
    File basedir = procCompile("compile/proc-only");
    mojos.assertBuildOutputs(new File(basedir, "target/generated-sources/annotations"),
        "proc/GeneratedSource.java");
  }

  @Test
  public void testProc() throws Exception {
    File basedir = procCompile("compile/proc");
    mojos.assertBuildOutputs(new File(basedir, "target"),
        "generated-sources/annotations/proc/GeneratedSource.java", "classes/proc/Source.class",
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
