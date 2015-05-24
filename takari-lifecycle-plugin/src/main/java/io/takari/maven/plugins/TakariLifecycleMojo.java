/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins;

import java.util.List;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;

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

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Inject
  protected RepositorySystem repositorySystem;

  @Inject
  protected MavenProjectHelper projectHelper;

  @Parameter(defaultValue = "${project}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected MavenProject project;

  @Parameter(defaultValue = "${reactorProjects}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected List<MavenProject> reactorProjects;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected RepositorySystemSession repositorySystemSession;

  @Parameter(defaultValue = "${project.remoteRepositories}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected List<RemoteRepository> remoteRepositories;

  @Parameter(defaultValue = "${mojoExecution}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected MojoExecution mojoExecution;

  @Parameter(defaultValue = "${mojoExecution.mojoDescriptor}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected MojoDescriptor mojoDescriptor;

  @Parameter(defaultValue = "${settings}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected Settings settings;

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
      logger.info(String.format("Skipping %s goal", mojoDescriptor.getGoal()));
      skipMojo();
      return;
    }

    executeMojo();
  }
}
