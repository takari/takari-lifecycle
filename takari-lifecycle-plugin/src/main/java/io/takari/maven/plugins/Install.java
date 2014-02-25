package io.takari.maven.plugins;

import io.takari.maven.plugins.util.AetherUtils;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * @author Jason van Zyl
 */
@Mojo(name = "install", defaultPhase = LifecyclePhase.INSTALL)
public class Install extends TakariLifecycleMojo {

  @Override
  public void executeMojo() throws MojoExecutionException {

    MavenProject lastProject = reactorProjects.get(reactorProjects.size() - 1);
    if (lastProject.equals(project)) {
      for (MavenProject reactorProject : reactorProjects) {
        installProject(reactorProject);
      }
    } else {
      getLog().info(
          "Installing " + project.getGroupId() + ":" + project.getArtifactId() + ":"
              + project.getVersion() + " at end");
    }
  }

  private void installProject(MavenProject project) throws MojoExecutionException {

    InstallRequest installRequest = new InstallRequest();

    //
    // Primary artifact
    //
    Artifact artifact = AetherUtils.toArtifact(project.getArtifact());
    installRequest.addArtifact(artifact);

    //
    // POM
    //
    Artifact pomArtifact = new SubArtifact(artifact, "", "pom");
    pomArtifact = pomArtifact.setFile(project.getFile());
    installRequest.addArtifact(pomArtifact);

    //
    // Attached artifacts
    //
    for (org.apache.maven.artifact.Artifact attachedArtifact : project.getAttachedArtifacts()) {
      installRequest.addArtifact(AetherUtils.toArtifact(attachedArtifact));
    }

    try {
      repositorySystem.install(repositorySystemSession, installRequest);
    } catch (InstallationException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }
}
