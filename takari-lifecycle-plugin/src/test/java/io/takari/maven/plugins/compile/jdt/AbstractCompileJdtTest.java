package io.takari.maven.plugins.compile.jdt;

import io.takari.maven.plugins.compile.CompileRule;
import io.takari.maven.testing.TestResources;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Rule;

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
    ArtifactHandler handler = mojos.getContainer().lookup(ArtifactHandler.class, "jar");
    DefaultArtifact artifact = new DefaultArtifact("test", artifactId, "1.0", Artifact.SCOPE_COMPILE, "jar", null, handler);
    artifact.setFile(file);
    Set<Artifact> artifacts = project.getArtifacts();
    artifacts.add(artifact);
    project.setArtifacts(artifacts);
    if (direct) {
      Set<Artifact> directDependencies = project.getDependencyArtifacts();
      directDependencies = directDependencies == null ? new LinkedHashSet<Artifact>() : new LinkedHashSet<>(directDependencies);
      directDependencies.add(artifact);
      project.setDependencyArtifacts(directDependencies);
    }
  }

}
