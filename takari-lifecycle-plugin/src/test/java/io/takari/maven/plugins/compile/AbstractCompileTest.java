package io.takari.maven.plugins.compile;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
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

  public static final boolean isJava7;

  static {
    boolean isJava7x = true;
    try {
      Class.forName("java.nio.file.Files");
    } catch (Exception e) {
      isJava7x = false;
    }
    isJava7 = isJava7x;
  }

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule() {
    @Override
    public MojoExecution newMojoExecution() {
      MojoExecution execution = super.newMojoExecution();
      execution.getConfiguration().addChild(newParameter("compilerId", compilerId));
      return execution;
    };
  };

  protected final String compilerId;

  protected AbstractCompileTest(String compilerId) {
    this.compilerId = compilerId;
  }

  @Parameters(name = "{0}")
  public static Iterable<Object[]> compilers() {
    return Arrays.<Object[]>asList( //
        new Object[] {"javac"} //
        , new Object[] {"forked-javac"} //
        , new Object[] {"jdt"} //
        );
  }

  protected File compile(String name, Xpp3Dom... parameters) throws Exception {
    File basedir = resources.getBasedir(name);
    return mojos.compile(basedir, parameters);
  }

  protected File compile(File basedir, Xpp3Dom... parameters) throws Exception {
    return mojos.compile(basedir, parameters);
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

  protected Xpp3Dom newParameter(String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    return child;
  }
}
