/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathDirectory;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;

import java.io.File;
import java.util.Collection;

import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

class OutputDirectoryClasspathEntry implements ClasspathEntry, MutableClasspathEntry {

  private final File directory;

  private ClasspathDirectory delegate;

  public OutputDirectoryClasspathEntry(File directory) {
    this.directory = directory;

    this.delegate = ClasspathDirectory.create(directory);
  }

  @Override
  public Collection<String> getPackageNames() {
    return delegate.getPackageNames();
  }

  @Override
  public NameEnvironmentAnswer findType(String packageName, String binaryFileName) {
    return delegate.findType(packageName, binaryFileName);
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
  public String getEntryName() {
    return directory.getAbsolutePath();
  }

  @Override
  public String getEntryDescription() {
    return getEntryName();
  }
}
