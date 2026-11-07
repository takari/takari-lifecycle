/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// integrate buildinfo: really this can't be packaged up in the JAR as it will prevent being
// idempotent
// how can we skip whole phases or at least be consistent
// how to decorate the lifecycle with additions i.e. a DAG within a phase
// include all the dependencies in the distribution, either use it as a repository or push them to
// the local repo on startup the first time
// we ultimately want simple JSR330 components
// offline model
//
public abstract class TakariLifecycleMojo extends AbstractMojo {

    private static final String ALTERNATE_LIFECYCLE_PROVIDING_PRIMARY_ARTIFACT =
            "@ALTERNATE_LIFECYCLE_PROVIDING_PRIMARY_ARTIFACT@";

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    protected RepositorySystem repositorySystem;

    @Inject
    protected MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${project}", readonly = true)
    @Incremental(configuration = Configuration.ignore)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}")
    @Incremental(configuration = Configuration.ignore)
    private MavenSession session;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    @Incremental(configuration = Configuration.ignore)
    protected RepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${mojoExecution.mojoDescriptor}", readonly = true)
    @Incremental(configuration = Configuration.ignore)
    protected MojoDescriptor mojoDescriptor;

    @Parameter(defaultValue = "false", property = "skip")
    @Incremental(configuration = Configuration.ignore)
    protected boolean skip;

    protected abstract void executeMojo() throws MojoExecutionException;

    protected void skipMojo() throws MojoExecutionException {
        // do nothing by default
    }

    @Override
    public final void execute() throws MojoExecutionException {

        // skip actually doesn't work here because it's on a per mojo basis

        if (skip) {
            logger.info("Skipping %s goal".formatted(mojoDescriptor.getGoal()));
            skipMojo();
            return;
        }

        executeMojo();
    }

    protected boolean alternateLifecycleProvidingPrimaryArtifact() {
        String alternateLifecycleProvidingPrimaryArtifact =
                session.getUserProperties().getProperty(ALTERNATE_LIFECYCLE_PROVIDING_PRIMARY_ARTIFACT);
        if (alternateLifecycleProvidingPrimaryArtifact != null
                && alternateLifecycleProvidingPrimaryArtifact.equals("true")) {
            return true;
        }
        return false;
    }

    protected boolean isFile(File file) {
        return file != null && file.isFile();
    }

    /**
     * Returns the artifacts to install/deploy for the project: pom, main artifact (if any) and attached artifacts.
     */
    protected List<Artifact> projectArtifacts() {
        List<Artifact> result = new ArrayList<>();

        Artifact projectArtifact = RepositoryUtils.toArtifact(project.getArtifact());
        Artifact pomArtifact = new SubArtifact(projectArtifact, "", "pom");
        pomArtifact = pomArtifact.setFile(project.getFile());

        if (ArtifactIdUtils.equalsId(pomArtifact, projectArtifact)) {
            if (isFile(projectArtifact.getFile())) {
                pomArtifact = projectArtifact;
            }
            projectArtifact = null;
        }

        result.add(pomArtifact);
        if (projectArtifact != null) {
            result.add(projectArtifact);
        }
        for (org.apache.maven.artifact.Artifact attachedArtifact : project.getAttachedArtifacts()) {
            result.add(RepositoryUtils.toArtifact(attachedArtifact));
        }
        return result;
    }
}
