package io.takari.maven.plugins.compile.jdt;

import java.io.File;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Rule;

import io.takari.maven.plugins.compile.CompileRule;
import io.takari.maven.testing.TestResources;

public abstract class AbstractCompileJdtTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule() {
    @Override
    public org.apache.maven.plugin.MojoExecution newMojoExecution() {
      MojoExecution execution = super.newMojoExecution();
      Xpp3Dom compilerId = new Xpp3Dom("compilerId");
      compilerId.setValue("jdt");
      execution.getConfiguration().addChild(compilerId);
      return execution;
    };
  };

  protected void addDependency(MavenProject project, String artifactId, File file) throws Exception {
    addDependency(project, artifactId, file, true);
  }

  protected void addDependency(MavenProject project, String artifactId, File file, boolean direct) throws Exception {
    mojos.newDependency(file).setArtifactId(artifactId).addTo(project, direct);
  }

}
