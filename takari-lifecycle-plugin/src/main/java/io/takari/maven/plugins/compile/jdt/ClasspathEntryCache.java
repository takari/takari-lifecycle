/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Named;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathDirectory;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathJar;
import io.takari.maven.plugins.compile.jdt.classpath.DependencyClasspathEntry;

@Named
public class ClasspathEntryCache {

  private static final Map<File, DependencyClasspathEntry> CACHE = new HashMap<>();

  public DependencyClasspathEntry get(File location) {
    location = normalize(location);
    synchronized (CACHE) {
      DependencyClasspathEntry entry = null;
      if (!CACHE.containsKey(location)) {
        if (location.isDirectory()) {
          entry = ClasspathDirectory.create(location);
        } else if (location.isFile()) {
          try {
            entry = ClasspathJar.create(location);
          } catch (IOException e) {
            // not a zip/jar, ignore
          }
        }
        CACHE.put(location, entry);
      } else {
        entry = CACHE.get(location);
      }
      return entry;
    }
  }

  private File normalize(File location) {
    try {
      location = location.getCanonicalFile();
    } catch (IOException e1) {
      location = location.getAbsoluteFile();
    }
    return location;
  }

  /**
   * @noreference this method is public for test purposes only
   */
  public static void flush() {
    synchronized (CACHE) {
      CACHE.clear();
    }
  }
}
