package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.spi.DefaultBuildContext;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;

public abstract class AbstractCompiler {

  protected final DefaultBuildContext<?> context;

  protected final AbstractCompileMojo config;

  protected AbstractCompiler(DefaultBuildContext<?> context, AbstractCompileMojo config) {
    this.context = context;
    this.config = config;
  }

  public abstract boolean setupClasspath(List<Artifact> dependencies) throws IOException;

  public abstract void compile(List<File> sources) throws MojoExecutionException, IOException;
}
