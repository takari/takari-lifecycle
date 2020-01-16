/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Named;

import io.takari.maven.plugins.compile.jdt.classpath.DependencyClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.PathNormalizer;

@Named
public class ClasspathEntryCache {

  public static interface Factory {
    DependencyClasspathEntry newClasspathEntry(Path location);
  }

  private static final Map<Path, CacheEntry> CACHE = new HashMap<>();

  private static final Map<Path, CacheEntry> SOURCEPATH_CACHE = new HashMap<>();

  public DependencyClasspathEntry get(Path location, CacheMode cacheMode, Factory factory) {
    return get(CACHE, location, cacheMode, factory);
  }

  public DependencyClasspathEntry getSourcepathEntry(Path location, CacheMode cacheMode, Factory factory) {
    return get(SOURCEPATH_CACHE, location, cacheMode, factory);
  }

  private static DependencyClasspathEntry get(Map<Path, CacheEntry> cache, Path location, CacheMode cacheMode, Factory factory) {
    location = PathNormalizer.getCanonicalPath(location);
    synchronized (cache) {
      CacheEntry entry = cache.get(location);
      CacheEntry newEntry = revalidate(location, entry, cacheMode, factory);
      if (entry != newEntry) {
        cache.put(location, entry = newEntry);
      }
      return entry.getCpe();
    }
  }

  private static CacheEntry revalidate(Path location, CacheEntry entry, CacheMode cacheMode, Factory factory) {
    // don't reload by default
    long length = -1;
    long lastModified = -1;

    if (cacheMode == CacheMode.PESSIMISTIC) {
      try {
        File file = location.toFile();
        if (file.isDirectory()) {
          // always reload dir entries
          length = 0;
          lastModified = System.currentTimeMillis();
        } else {
          // check if file has changed
          length = file.length();
          lastModified = file.lastModified();
        }
      } catch (UnsupportedOperationException e) {
        // JRT Path doesn't support toFile --> JDK not expected change so don't reload
      }
    }

    if (entry == null || entry.getSize() != length || entry.getTimestamp() != lastModified) {
      entry = new CacheEntry(factory.newClasspathEntry(location), lastModified, length);
    }

    return entry;
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

  public static class CacheEntry {
    private final DependencyClasspathEntry cpe;
    private final long timestamp;
    private final long size;

    public CacheEntry(DependencyClasspathEntry cpe, long timestamp, long size) {
      this.cpe = cpe;
      this.timestamp = timestamp;
      this.size = size;
    }

    public DependencyClasspathEntry getCpe() {
      return cpe;
    }

    public long getSize() {
      return size;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }

  public enum CacheMode {
    DEFAULT, PESSIMISTIC;
  }
}
