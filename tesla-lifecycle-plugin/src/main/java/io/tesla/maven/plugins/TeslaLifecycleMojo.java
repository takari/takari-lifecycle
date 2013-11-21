package io.tesla.maven.plugins;

import java.util.List;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.sonatype.maven.plugin.Conf;

// integrate buildinfo: really this can't be packaged up in the JAR as it will prevent being idempotent
// how can we skip whole phases or at least be consistent
// how to decorate the lifecycle with additions i.e. a DAG within a phase
// integrate incremental build
// include all the dependencies in the distribution, either use it as a repository or push them to the local repo on startup the first time
// builds must be idempotent
// we ultimately want simple JSR330 components
// offline model
//
public abstract class TeslaLifecycleMojo extends AbstractMojo {

  @Inject
  protected RepositorySystem repositorySystem;

  @Inject 
  protected Logger logger;
  
  @Inject
  protected MavenProjectHelper projectHelper;
  
  @Conf(defaultValue = "${project}")
  protected MavenProject project;

  @Conf(defaultValue = "${reactorProjects}")
  protected List<MavenProject> reactorProjects;

  @Conf(defaultValue = "${repositorySystemSession}")
  protected RepositorySystemSession repositorySystemSession;

  @Conf(defaultValue = "${project.remoteRepositories}")
  protected List<RemoteRepository> remoteRepositories;

  @Conf(defaultValue = "${mojoDescriptor}")
  protected MojoDescriptor mojoDescriptor;

  @Conf(defaultValue = "${settings}")
  protected Settings settings;

  @Conf(defaultValue = "false", property="skip")
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
