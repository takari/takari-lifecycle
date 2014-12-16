/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public class ClasspathJar implements ClasspathEntry {

  private final File file;
  private final ZipFile zipFile;
  private final Set<String> packageNames;

  public ClasspathJar(File file) throws IOException {
    this.file = file;
    this.zipFile = new ZipFile(this.file);
    this.packageNames = Collections.unmodifiableSet(initializePackageCache(zipFile));
  }

  private static Set<String> initializePackageCache(ZipFile zipFile) {
    Set<String> result = new HashSet<String>();
    for (Enumeration e = zipFile.entries(); e.hasMoreElements();) {
      ZipEntry entry = (ZipEntry) e.nextElement();
      String name = entry.getName();
      int last = name.lastIndexOf('/');
      while (last > 0) {
        name = name.substring(0, last);
        result.add(name);
        last = name.lastIndexOf('/');
      }
    }
    return result;
  }

  @Override
  public Collection<String> getPackageNames() {
    return packageNames;
  }

  @Override
  public NameEnvironmentAnswer findType(String packageName, String binaryFileName) {
    if (!packageNames.contains(packageName)) {
      return null;
    }
    try {
      String qualifiedFileName = packageName + "/" + binaryFileName;
      ClassFileReader reader = ClassFileReader.read(this.zipFile, qualifiedFileName);
      if (reader != null) return new NameEnvironmentAnswer(reader, null);
    } catch (ClassFormatException e) {
      // treat as if class file is missing
    } catch (IOException e) {
      // treat as if class file is missing
    }
    return null;
  }

  @Override
  public String toString() {
    return "Classpath for jar file " + file.getPath(); //$NON-NLS-1$
  }

  @Override
  public String getEntryName() {
    return file.getAbsolutePath();
  }
}
