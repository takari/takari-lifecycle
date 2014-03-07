package io.takari.maven.plugins.compile;

import static org.apache.maven.plugin.testing.resources.TestResources.cp;
import io.takari.maven.plugins.compiler.incremental.AbstractCompileMojo.Proc;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AbstractCompileTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule();

  private final String compilerId;

  private final boolean fork;

  protected AbstractCompileTest(String compilerId, boolean fork) {
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

  protected File compile(String name) throws Exception {
    File basedir = resources.getBasedir(name);
    return compile(basedir);
  }

  protected File compile(File basedir, Xpp3Dom... parameters) throws Exception {
    MavenProject project = mojos.readMavenProject(basedir);
    compile(project, parameters);
    return basedir;
  }

  protected void compile(MavenProject project, Xpp3Dom... parameters) throws Exception {
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = newMojoExecution();

    if (parameters != null) {
      Xpp3Dom configuration = execution.getConfiguration();
      for (Xpp3Dom parameter : parameters) {
        configuration.addChild(parameter);
      }
    }

    mojos.executeMojo(session, project, execution);
  }

  protected MojoExecution newMojoExecution() {
    MojoExecution execution = mojos.newMojoExecution("compile-incremental");

    Xpp3Dom configuration = execution.getConfiguration();
    add(configuration, "fork", Boolean.toString(fork));
    return execution;
  }

  protected void addDependency(MavenProject project, String artifactId, File file) throws Exception {
    ArtifactHandler handler = mojos.getContainer().lookup(ArtifactHandler.class, "jar");
    DefaultArtifact artifact =
        new DefaultArtifact("test", artifactId, "1.0", Artifact.SCOPE_COMPILE, "jar", null, handler);
    artifact.setFile(file);
    Set<Artifact> artifacts = project.getArtifacts();
    artifacts.add(artifact);
    project.setArtifacts(artifacts);
  }

  private void add(Xpp3Dom configuration, String name, String value) {
    configuration.addChild(newParameter(name, value));
  }

  protected Xpp3Dom newParameter(String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    return child;
  }

  protected File procCompile(String projectName, Proc proc) throws Exception, IOException {
    File processor = compile("compile/processor");
    cp(processor, "src/main/resources/META-INF/services/javax.annotation.processing.Processor",
        "target/classes/META-INF/services/javax.annotation.processing.Processor");

    File basedir = resources.getBasedir(projectName);
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = newMojoExecution();

    addDependency(project, "processor", new File(processor, "target/classes"));

    if (proc != null) {
      add(execution.getConfiguration(), "proc", proc.name());
    }

    mojos.executeMojo(session, project, execution);
    return basedir;
  }

}
