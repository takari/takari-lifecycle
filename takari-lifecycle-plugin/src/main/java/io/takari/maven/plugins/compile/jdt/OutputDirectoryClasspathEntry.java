/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.util.Collection;

import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathDirectory;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;

class OutputDirectoryClasspathEntry implements ClasspathEntry, MutableClasspathEntry {

  private final File directory;

  /**
   * <strong>Live</strong> collection of output files to ignore. New files are added to the collection during lifespan of this OutputDirectoryClasspathEntry instance. The idea is to hide to-be-deleted
   * files from classpath.
   */
  private final Collection<File> staleOutputs;

  private ClasspathDirectory delegate;

  /**
   * @param staleOutputs is a <strong>live</strong> collection of output files to ignore.
   */
  public OutputDirectoryClasspathEntry(File directory, Collection<File> staleOutputs) {
    this.directory = directory;
    this.staleOutputs = staleOutputs;

    this.delegate = ClasspathDirectory.create(directory);
  }

  @Override
  public Collection<String> getPackageNames() {
    return delegate.getPackageNames();
  }

  @Override
  public NameEnvironmentAnswer findType(String packageName, String typeName) {
    if (!staleOutputs.contains(delegate.getFile(packageName, typeName))) {
      return delegate.findType(packageName, typeName, null);
    }
    return null;
  }

  @Override
  public void reset() {
    this.delegate = ClasspathDirectory.create(directory);
  }

  @Override
  public String toString() {
    return "Classpath for output directory " + directory;
  }

  @Override
  public String getEntryDescription() {
    return directory.getAbsolutePath();
  }
}
