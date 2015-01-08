/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins;

import io.takari.maven.plugins.util.AetherUtils;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * @author Jason van Zyl
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY, configurator = "takari")
public class Deploy extends TakariLifecycleMojo {

  // TODO deploy at the end to prevent corruption

  // polyglot conversion to detect the project type and convert on the way out the door
  // how to help people fork projects and deploy elsewhere: the alt deployment repo may have issues
  // - a standard property to replace in a conventional way?

  @Override
  public void executeMojo() throws MojoExecutionException {
    installProject(project);
  }

  private void installProject(MavenProject project) throws MojoExecutionException {

    DeployRequest deployRequest = new DeployRequest();

    if ("pom".equals(project.getPackaging())) {
      //
      // POM-project primary artifact
      //
      Artifact artifact = AetherUtils.toArtifact(project.getArtifact());
      artifact = artifact.setFile(project.getFile());
      deployRequest.addArtifact(artifact);

    } else {
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
    }

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
    return AetherUtils.toRepo(project.getDistributionManagementArtifactRepository());
  }
}
