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
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Named;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathDirectory;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathJar;
import io.takari.maven.plugins.compile.jdt.classpath.DependencyClasspathEntry;

@Named
public class ClasspathEntryCache {

  private static class Key {
    public final File file;
    public final Charset encoding;

    public Key(File file, Charset encoding) {
      this.file = file;
      this.encoding = encoding;
    }

    @Override
    public int hashCode() {
      int hash = 31;
      hash = hash * 17 + file.hashCode();
      hash = hash * 17 + (encoding != null ? encoding.hashCode() : 0);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }

      if (!(obj instanceof Key)) {
        return false;
      }

      Key other = (Key) obj;

      return eq(file, other.file) && eq(encoding, other.encoding);
    }
  }

  private static <T> boolean eq(T a, T b) {
    return b != null ? a.equals(b) : b == null;
  }

  private static final Map<Key, DependencyClasspathEntry> CACHE = new HashMap<>();

  public DependencyClasspathEntry get(File location, Charset encoding) {
    final Key key = newKey(location, encoding);
    synchronized (CACHE) {
      DependencyClasspathEntry entry = null;
      if (!CACHE.containsKey(key)) {
        if (key.file.isDirectory()) {
          entry = ClasspathDirectory.create(key.file);
        } else if (key.file.isFile()) {
          try {
            entry = ClasspathJar.create(key.file);
          } catch (IOException e) {
            // not a zip/jar, ignore
          }
        }
        CACHE.put(key, entry);
      } else {
        entry = CACHE.get(key);
      }
      return entry;
    }
  }

  private Key newKey(File location, Charset encoding) {
    try {
      location = location.getCanonicalFile();
    } catch (IOException e1) {
      location = location.getAbsoluteFile();
    }
    return new Key(location, encoding);
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
