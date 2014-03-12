package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.DefaultBuildContext;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

@Named
@MojoExecutionScoped
public class ProjectClasspathDigester {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final DefaultBuildContext<?> context;

  @Inject
  public ProjectClasspathDigester(DefaultBuildContext<?> context, MavenProject project,
      MavenSession session) {
    this.context = context;
  }

  /**
   * Detects if classpath dependencies changed compared to the previous build or not.
   */
  public boolean digestDependencies(List<Artifact> dependencies) throws IOException {
    log.info("Analyzing {} classpath dependencies", dependencies.size());

    Stopwatch stopwatch = new Stopwatch().start();

    boolean changed = false;

    for (Artifact dependency : dependencies) {
      File file = dependency.getFile();
      if (!file.exists()) {
        // happens with reactor dependencies with empty source folders
        continue;
      }
      final InputMetadata<ArtifactFile> input = context.registerInput(new ArtifactFileHolder(file));
      // XXX this scans directories twice, need to compare state-to-state
      changed = changed || input.getStatus() != ResourceStatus.UNMODIFIED;
    }

    changed = changed || !context.getRemovedInputs(ArtifactFile.class).isEmpty();

    log.info("Analyzed {} classpath dependencies ({} ms)", dependencies.size(),
        stopwatch.elapsed(TimeUnit.MILLISECONDS));

    return changed;
  }


}
