/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.SubArtifact;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;
import io.takari.maven.plugins.install_deploy.DeployParticipant;
import io.takari.maven.plugins.util.AetherUtils;

/**
 * @author Jason van Zyl
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY, configurator = "takari")
public class Deploy extends TakariLifecycleMojo {

  // TODO deploy at the end to prevent corruption

  // polyglot conversion to detect the project type and convert on the way out the door

  /**
   * Specifies an alternative repository to which the project artifacts should be deployed (other than those specified in {@code <distributionManagement>}).
   * <p/>
   * Format: id::layout::url
   */
  @Parameter(property = "altDeploymentRepository")
  private String altDeploymentRepository;

  @Parameter(defaultValue = "false", property = "deployAtEnd")
  @Incremental(configuration = Configuration.ignore)
  protected boolean deployAtEnd;

  @Inject
  private DeployParticipant deployParticipant;

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

    if (!deployAtEnd) {
      try {
        deployParticipant.deploy(repositorySystemSession, deployRequest);
      } catch (DeploymentException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    } else {
      getLog().info("Will deploy " + project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion() + " at the end of build");
      deployParticipant.deployAtEnd(deployRequest);
    }
  }

  //
  // All this logic about finding the right repository needs to be standardized
  //
  public RemoteRepository remoteRepository(MavenProject project) throws MojoExecutionException {
    if (altDeploymentRepository != null) {
      Matcher matcher = Pattern.compile("(.+)::(.+)::(.+)").matcher(altDeploymentRepository);
      if (!matcher.matches()) {
        throw new MojoExecutionException(altDeploymentRepository, "Invalid syntax for repository.", "Invalid syntax for alternative repository. Use \"id::layout::url\".");
      }

      String id = matcher.group(1).trim();
      String layout = matcher.group(2).trim();
      String url = matcher.group(3).trim();

      RemoteRepository.Builder builder = new RemoteRepository.Builder(id, layout, url);

      return builder.build();
    }

    return AetherUtils.toRepo(project.getDistributionManagementArtifactRepository());
  }
}
