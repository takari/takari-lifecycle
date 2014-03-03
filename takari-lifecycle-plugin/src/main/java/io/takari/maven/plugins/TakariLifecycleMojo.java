package io.takari.maven.plugins;

import io.takari.incrementalbuild.configuration.Configuration;

import java.util.List;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
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

// integrate buildinfo: really this can't be packaged up in the JAR as it will prevent being
// idempotent
// how can we skip whole phases or at least be consistent
// how to decorate the lifecycle with additions i.e. a DAG within a phase
// integrate incremental build
// include all the dependencies in the distribution, either use it as a repository or push them to
// the local repo on startup the first time
// builds must be idempotent
// we ultimately want simple JSR330 components
// offline model
//
public abstract class TakariLifecycleMojo extends AbstractMojo {

  // TODO review @Configuration(ignored=true) parameters and if they should not be ignored

  @Inject
  protected RepositorySystem repositorySystem;

  @Inject
  protected Logger logger;

  @Inject
  protected MavenProjectHelper projectHelper;

  @Parameter(defaultValue = "${project}")
  @Configuration(ignored = true)
  protected MavenProject project;

  @Parameter(defaultValue = "${reactorProjects}")
  @Configuration(ignored = true)
  protected List<MavenProject> reactorProjects;

  @Parameter(defaultValue = "${repositorySystemSession}")
  @Configuration(ignored = true)
  protected RepositorySystemSession repositorySystemSession;

  @Parameter(defaultValue = "${project.remoteRepositories}")
  @Configuration(ignored = true)
  protected List<RemoteRepository> remoteRepositories;

  @Parameter(defaultValue = "${mojoExecution.mojoDescriptor}")
  @Configuration(ignored = true)
  protected MojoDescriptor mojoDescriptor;

  @Parameter(defaultValue = "${settings}")
  @Configuration(ignored = true)
  protected Settings settings;

  @Parameter(defaultValue = "false", property = "skip")
  protected boolean skip;

  protected abstract void executeMojo() throws MojoExecutionException;

  public void execute() throws MojoExecutionException {

    // skip actually doesn't work here becaues it's on a per mojo basis

    if (skip) {
      logger.info(String.format("Skipping %s goal", mojoDescriptor.getExecuteGoal()));
      return;
    }

    executeMojo();
  }
}
