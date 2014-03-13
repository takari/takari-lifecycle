package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.spi.DefaultBuildContext;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

@Named
@MojoExecutionScoped
public class ProjectClasspathDigester {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final Map<File, ArtifactFile> CACHE = new ConcurrentHashMap<File, ArtifactFile>();

  private final DefaultBuildContext<?> context;

  @Inject
  public ProjectClasspathDigester(DefaultBuildContext<?> context, MavenProject project,
      MavenSession session) {
    this.context = context;

    // this is only needed for unit tests, but won't hurt in general
    CACHE.remove(new File(project.getBuild().getOutputDirectory()));
    CACHE.remove(new File(project.getBuild().getTestOutputDirectory()));
  }

  /**
   * Detects if classpath dependencies changed compared to the previous build or not.
   */
  public boolean digestDependencies(List<Artifact> dependencies) throws IOException {
    log.info("Analyzing {} classpath dependencies", dependencies.size());

    Stopwatch stopwatch = new Stopwatch().start();

    boolean changed = false;

    Map<File, ArtifactFile> previousArtifacts = getPreviousDependencies();

    for (Artifact dependency : dependencies) {
      File file = dependency.getFile();

      ArtifactFile previousArtifact = previousArtifacts.get(file);
      ArtifactFile artifact = CACHE.get(file);
      if (artifact == null) {
        if (file.isFile()) {
          artifact = newFileArtifact(file, previousArtifact);
        } else if (file.isDirectory()) {
          artifact = newDirectoryArtifact(file, previousArtifact);
        } else {
          // happens with reactor dependencies with empty source folders
          continue;
        }
        CACHE.put(file, artifact);
      }

      context.registerInput(new ArtifactFileHolder(artifact));

      if (hasChanged(artifact, previousArtifact)) {
        changed = true;
        log.debug("New or changed classpath entry {}", file);
      }
    }

    for (InputMetadata<ArtifactFile> removedArtifact : context.getRemovedInputs(ArtifactFile.class)) {
      changed = true;
      log.debug("Removed classpath entry {}", removedArtifact.getResource().file);
    }

    log.debug("Analyzed {} classpath dependencies ({} ms)", dependencies.size(),
        stopwatch.elapsed(TimeUnit.MILLISECONDS));

    return changed;
  }

  private boolean hasChanged(ArtifactFile artifact, ArtifactFile previousArtifact) {
    if (previousArtifact == null) {
      return true;
    }
    return artifact.lastModified != previousArtifact.lastModified
        || artifact.length != previousArtifact.length;
  }

  private ArtifactFile newDirectoryArtifact(File directory, ArtifactFile previousArtifact) {
    StringBuilder msg = new StringBuilder();
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(directory);
    scanner.scan();
    long maxLastModified = 0, fileCount = 0;
    for (String path : scanner.getIncludedFiles()) {
      File file = new File(directory, path);
      long lastModified = file.lastModified();
      maxLastModified = Math.max(maxLastModified, lastModified);
      fileCount++;
      if (previousArtifact != null && previousArtifact.lastModified < lastModified) {
        msg.append("\n   new or modfied class folder member ").append(file);
      }
    }

    if (previousArtifact != null && previousArtifact.length != fileCount) {
      msg.append("\n   classfolder member count changed (new ").append(fileCount)
          .append(" previous ").append(previousArtifact.length).append(')');
    }

    if (msg.length() > 0) {
      log.debug("Changed dependency class folder {}: {}", directory, msg.toString());
    }

    return new ArtifactFile(directory, false, fileCount, maxLastModified);
  }

  private ArtifactFile newFileArtifact(File file, ArtifactFile previousArtifact) {
    return new ArtifactFile(file, true, file.length(), file.lastModified());
  }

  private Map<File, ArtifactFile> getPreviousDependencies() {
    Map<File, ArtifactFile> result = new HashMap<File, ArtifactFile>();

    for (InputMetadata<ArtifactFile> metadata : context.getRegisteredInputs(ArtifactFile.class)) {
      ArtifactFile artifact = metadata.getResource();
      result.put(artifact.file, artifact);
    }

    return result;
  }
}
