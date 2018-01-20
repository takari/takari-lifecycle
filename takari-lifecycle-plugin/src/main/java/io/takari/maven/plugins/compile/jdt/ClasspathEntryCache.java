/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Named;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathDirectory;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathJar;
import io.takari.maven.plugins.compile.jdt.classpath.DependencyClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.PathNormalizer;
import io.takari.maven.plugins.compile.jdt.classpath.SourcepathDirectory;

@Named
public class ClasspathEntryCache {

  private static interface Factory {
    DependencyClasspathEntry newClasspathEntry();
  }

  private static final Map<Path, DependencyClasspathEntry> CACHE = new HashMap<>();

  private static final Map<Path, DependencyClasspathEntry> SOURCEPATH_CACHE = new HashMap<>();

  public DependencyClasspathEntry get(Path location) {
    return get(CACHE, location, () -> {
      DependencyClasspathEntry entry = null;
      if (Files.isDirectory(location)) {
        entry = ClasspathDirectory.create(location);
      } else if (Files.isRegularFile(location)) {
        try {
          entry = ClasspathJar.create(location);
        } catch (IOException e) {
          // not a zip/jar, ignore
        }
      }
      return entry;
    });
  }

  public DependencyClasspathEntry getSourcepathEntry(Path location, Charset encoding) {
    return get(SOURCEPATH_CACHE, location, () -> SourcepathDirectory.create(location, encoding));
  }

  private static DependencyClasspathEntry get(Map<Path, DependencyClasspathEntry> cache, Path location, Factory factory) {
    location = PathNormalizer.getCanonicalPath(location);
    synchronized (cache) {
      DependencyClasspathEntry entry = null;
      if (!cache.containsKey(location)) {
        entry = factory.newClasspathEntry();
        cache.put(location, entry);
      } else {
        entry = cache.get(location);
      }
      return entry;
    }
  }

  /**
   * @noreference this method is public for test purposes only
   */
  public static void flush() {
    synchronized (CACHE) {
      CACHE.clear();
    }
    synchronized (SOURCEPATH_CACHE) {
      SOURCEPATH_CACHE.clear();
    }
  }
}
