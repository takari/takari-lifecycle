/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.javac;

import io.takari.maven.plugins.compile.CompilerBuildContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

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
  private static final String ATTR_CLASSPATH_DIGEST = "javac.classpath.digest";

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final Map<File, ArtifactFile> CACHE = new ConcurrentHashMap<File, ArtifactFile>();

  private final CompilerBuildContext context;

  @Inject
  public ProjectClasspathDigester(CompilerBuildContext context, MavenProject project, MavenSession session) {
    this.context = context;

    // this is only needed for unit tests, but won't hurt in general
    CACHE.remove(new File(project.getBuild().getOutputDirectory()));
    CACHE.remove(new File(project.getBuild().getTestOutputDirectory()));
  }

  /**
   * Detects if classpath dependencies changed compared to the previous build or not.
   */
  public boolean digestDependencies(List<File> dependencies) throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();

    Map<File, ArtifactFile> previousArtifacts = getPreviousDependencies();
    LinkedHashMap<File, ArtifactFile> digest = new LinkedHashMap<>();

    for (File dependency : dependencies) {
      ArtifactFile previousArtifact = previousArtifacts.get(dependency);
      ArtifactFile artifact = CACHE.get(dependency);
      if (artifact == null) {
        if (dependency.isFile()) {
          artifact = newFileArtifact(dependency, previousArtifact);
        } else if (dependency.isDirectory()) {
          artifact = newDirectoryArtifact(dependency, previousArtifact);
        } else {
          // happens with reactor dependencies with empty source folders
          continue;
        }
        CACHE.put(dependency, artifact);
      }

      digest.put(dependency, artifact);

      if (equals(artifact, previousArtifact)) {
        log.debug("New or changed classpath entry {}", dependency);
      }
    }

    for (File reviousDependency : previousArtifacts.keySet()) {
      if (!digest.containsKey(reviousDependency)) {
        log.debug("Removed classpath entry {}", reviousDependency);
      }
    }

    boolean changed = !equals(digest.values(), previousArtifacts.values());

    context.setAttribute(ATTR_CLASSPATH_DIGEST, new ArrayList<>(digest.values()));

    log.debug("Analyzed {} classpath dependencies ({} ms)", dependencies.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));

    return changed;
  }

  private boolean equals(Collection<ArtifactFile> a, Collection<ArtifactFile> b) {
    if (a.size() != b.size()) {
      return false;
    }
    Iterator<ArtifactFile> ia = a.iterator();
    Iterator<ArtifactFile> ib = b.iterator();
    while (ia.hasNext()) {
      if (!equals(ia.next(), ib.next())) {
        return false;
      }
    }
    return true;
  }

  private boolean equals(ArtifactFile a, ArtifactFile b) {
    if (a == null) {
      return b == null;
    }
    if (b == null) {
      return false;
    }
    return a.file.equals(b.file) && a.isFile == b.isFile && a.lastModified == b.lastModified && a.length == b.length;
  }

  private ArtifactFile newDirectoryArtifact(File directory, ArtifactFile previousArtifact) {
    StringBuilder msg = new StringBuilder();
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(directory);
    scanner.setIncludes(new String[] {"**/*.class"});
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
      msg.append("\n   classfolder member count changed (new ").append(fileCount).append(" previous ").append(previousArtifact.length).append(')');
    }

    if (msg.length() > 0) {
      log.debug("Changed dependency class folder {}: {}", directory, msg.toString());
    }

    return new ArtifactFile(directory, false, fileCount, maxLastModified);
  }

  private ArtifactFile newFileArtifact(File file, ArtifactFile previousArtifact) {
    return new ArtifactFile(file, true, file.length(), file.lastModified());
  }

  @SuppressWarnings("unchecked")
  private Map<File, ArtifactFile> getPreviousDependencies() {
    LinkedHashMap<File, ArtifactFile> digest = new LinkedHashMap<>();
    ArrayList<ArtifactFile> artifacts = context.getAttribute(ATTR_CLASSPATH_DIGEST, true, ArrayList.class);
    if (artifacts == null) {
      return Collections.emptyMap();
    }
    for (ArtifactFile artifact : artifacts) {
      digest.put(artifact.file, artifact);
    }
    return digest;
  }
}
