package io.takari.maven.plugins;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;
import io.takari.incrementalbuild.spi.DefaultBuildContext;

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

  @Inject
  private DefaultBuildContext<?> context;

  @Parameter(defaultValue = "${project}")
  @Incremental(configuration = Configuration.ignore)
  protected MavenProject project;

  @Parameter(defaultValue = "${reactorProjects}")
  @Incremental(configuration = Configuration.ignore)
  protected List<MavenProject> reactorProjects;

  @Parameter(defaultValue = "${repositorySystemSession}")
  @Incremental(configuration = Configuration.ignore)
  protected RepositorySystemSession repositorySystemSession;

  @Parameter(defaultValue = "${project.remoteRepositories}")
  @Incremental(configuration = Configuration.ignore)
  protected List<RemoteRepository> remoteRepositories;

  @Parameter(defaultValue = "${mojoExecution}")
  @Incremental(configuration = Configuration.ignore)
  protected MojoExecution mojoExecution;

  @Parameter(defaultValue = "${mojoExecution.mojoDescriptor}")
  @Incremental(configuration = Configuration.ignore)
  protected MojoDescriptor mojoDescriptor;

  @Parameter(defaultValue = "${settings}")
  @Incremental(configuration = Configuration.ignore)
  protected Settings settings;

  @Parameter(defaultValue = "false", property = "skip")
  protected boolean skip;

  protected abstract void executeMojo() throws MojoExecutionException;

  @Override
  public final void execute() throws MojoExecutionException {

    // skip actually doesn't work here because it's on a per mojo basis

    if (skip) {
      logger.info(String.format("Skipping %s goal", mojoDescriptor.getGoal()));
      context.markSkipExecution();
      return;
    }

    executeMojo();
  }
}
