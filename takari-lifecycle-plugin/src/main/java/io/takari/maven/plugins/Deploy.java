package io.takari.maven.plugins;

import io.takari.maven.plugins.util.AetherUtils;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RemoteRepository.Builder;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

/**
 * @author Jason van Zyl
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY)
public class Deploy extends TakariLifecycleMojo {

  // deploy at the end to prevent corruption
  // polyglot conversion to detect the project type and convert on the way out the door
  // how to help people fork projects and deploy elsewhere: the alt deployment repo may have issues
  // - a standard property to replace in a conventional way?

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

    DeployRequest deployRequest = new DeployRequest();

    //
    // Primary artifact
    //
    Artifact artifact = AetherUtils.toArtifact(project.getArtifact());
    deployRequest.addArtifact(artifact);

    //
    // POM
    //
    Artifact pomArtifact = new SubArtifact(artifact, "", "pom");
    pomArtifact = pomArtifact.setFile(project.getFile());
    deployRequest.addArtifact(pomArtifact);

    //
    // Attached artifacts
    //
    for (org.apache.maven.artifact.Artifact attachedArtifact : project.getAttachedArtifacts()) {
      deployRequest.addArtifact(AetherUtils.toArtifact(attachedArtifact));
    }

    deployRequest.setRepository(remoteRepository(project));

    try {
      repositorySystem.deploy(repositorySystemSession, deployRequest);
    } catch (DeploymentException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  //
  // All this logic about finding the right repository needs to be standardized
  //
  public RemoteRepository remoteRepository(MavenProject project) {

    // alternate repository
    // make it easy to fork and deploy
    // offline

    DeploymentRepository deploymentRepository;

    if (ArtifactUtils.isSnapshot(project.getVersion())) {
      deploymentRepository = project.getDistributionManagement().getSnapshotRepository();
    } else {
      deploymentRepository = project.getDistributionManagement().getRepository();
    }

    Builder remoteRepositoryBuilder =
        new RemoteRepository.Builder(deploymentRepository.getId(), "default",
            deploymentRepository.getUrl());

    Server server = settings.getServer(deploymentRepository.getId());
    if (server != null) {
      if (server.getUsername() != null && server.getPassword() != null) {
        Authentication authentication = new AuthenticationBuilder() //
            .addUsername(server.getUsername()) //
            .addPassword(server.getPassword()) //
            .build();
        remoteRepositoryBuilder.setAuthentication(authentication);
      }
    }

    return remoteRepositoryBuilder.build();
  }
}
