/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;
import io.takari.maven.plugins.install_deploy.DeployParticipant;
import io.takari.maven.plugins.util.AetherUtils;
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
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * @author Jason van Zyl
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY, configurator = "takari", threadSafe = true)
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
        deployProject(project);
    }

    private void deployProject(MavenProject project) throws MojoExecutionException {
        DeployRequest deployRequest = new DeployRequest();

        Artifact projectArtifact = AetherUtils.toArtifact(project.getArtifact());
        Artifact pomArtifact = new SubArtifact(projectArtifact, "", "pom");
        pomArtifact = pomArtifact.setFile(project.getFile());

        if (ArtifactIdUtils.equalsId(pomArtifact, projectArtifact)) {
            if (isFile(projectArtifact.getFile())) {
                pomArtifact = projectArtifact;
            }
            projectArtifact = null;
        }

        deployRequest.addArtifact(pomArtifact);
        if (projectArtifact != null) {
            deployRequest.addArtifact(projectArtifact);
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
            getLog().info("Will deploy " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                    + project.getVersion() + " at the end of build");
            deployParticipant.deployAtEnd(deployRequest);
        }
    }

    private static final Pattern MODERN_REPOSITORY_PATTERN = Pattern.compile("(.+)::(.+)");
    private static final Pattern LEGACY_REPOSITORY_PATTERN = Pattern.compile("(.+)::(.+)::(.+)");

    //
    // All this logic about finding the right repository needs to be standardized
    //
    public RemoteRepository remoteRepository(MavenProject project) throws MojoExecutionException {
        if (altDeploymentRepository != null) {
            String id = null;
            String layout = "default";
            String url = null;
            Matcher matcher = MODERN_REPOSITORY_PATTERN.matcher(altDeploymentRepository);
            if (matcher.matches()) {
                id = matcher.group(1).trim();
                url = matcher.group(2).trim();
            } else {
                matcher = LEGACY_REPOSITORY_PATTERN.matcher(altDeploymentRepository);
                if (matcher.matches()) {
                    id = matcher.group(1).trim();
                    layout = matcher.group(2).trim();
                    url = matcher.group(3).trim();
                }
            }

            if (id == null || id.isEmpty() || url == null || url.isEmpty()) {
                throw new MojoExecutionException(
                        altDeploymentRepository,
                        "Invalid syntax for repository.",
                        "Invalid syntax for alternative repository. Use \"id::url\" or \"id::layout::url\".");
            }

            RemoteRepository.Builder builder = new RemoteRepository.Builder(id, layout, url);

            // Retrieve the appropriate authentication
            final AuthenticationSelector authenticationSelector = repositorySystemSession.getAuthenticationSelector();
            final Authentication authentication = authenticationSelector.getAuthentication(builder.build());
            builder.setAuthentication(authentication);

            return builder.build();
        }

        return AetherUtils.toRepo(project.getDistributionManagementArtifactRepository());
    }
}
