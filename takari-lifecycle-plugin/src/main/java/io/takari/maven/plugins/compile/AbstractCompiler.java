package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultOutputMetadata;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCompiler {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected final DefaultBuildContext<?> context;

  protected final AbstractCompileMojo config;

  protected AbstractCompiler(DefaultBuildContext<?> context, AbstractCompileMojo config) {
    this.context = context;
    this.config = config;
  }

  public abstract boolean setClasspath(List<Artifact> dependencies) throws IOException;

  public abstract boolean setSources(List<File> sources) throws IOException;

  public abstract void setModifiedOutputs(Set<DefaultOutputMetadata> outputs);

  public abstract void compile() throws MojoExecutionException, IOException;

  public abstract void skipCompilation();
}
