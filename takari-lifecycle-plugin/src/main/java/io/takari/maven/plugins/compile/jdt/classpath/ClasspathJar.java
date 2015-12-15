/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import static org.eclipse.jdt.internal.compiler.util.SuffixConstants.SUFFIX_STRING_class;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.osgi.framework.BundleException;

public class ClasspathJar extends DependencyClasspathEntry implements ClasspathEntry {

  private final ZipFile zipFile;

  private ClasspathJar(File file, ZipFile zipFile, Collection<String> packageNames, Collection<String> exportedPackages) throws IOException {
    super(file, packageNames, exportedPackages);
    this.zipFile = zipFile;
  }

  private static Set<String> getPackageNames(ZipFile zipFile) {
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
  public NameEnvironmentAnswer findType(String packageName, String typeName, AccessRestriction accessRestriction) {
    try {
      String qualifiedFileName = packageName + "/" + typeName + SUFFIX_STRING_class;
      ClassFileReader reader = ClassFileReader.read(this.zipFile, qualifiedFileName);
      if (reader != null) {
        return new NameEnvironmentAnswer(reader, accessRestriction);
      }
    } catch (ClassFormatException | IOException e) {
      // treat as if class file is missing
    }
    return null;
  }

  @Override
  public String toString() {
    return "Classpath for jar file " + file.getPath(); //$NON-NLS-1$
  }

  public static ClasspathJar create(File file) throws IOException {
    ZipFile zipFile = new ZipFile(file);
    Set<String> packageNames = getPackageNames(zipFile);
    Collection<String> exportedPackages = null;
    // TODO do not look for exported packages in java standard library
    ZipEntry entry = zipFile.getEntry(PATH_EXPORT_PACKAGE);
    if (entry != null) {
      try (InputStream is = zipFile.getInputStream(entry)) {
        exportedPackages = parseExportPackage(is);
      }
    }
    if (exportedPackages == null) {
      entry = zipFile.getEntry(PATH_MANIFESTMF);
      if (entry != null) {
        try (InputStream is = zipFile.getInputStream(entry)) {
          exportedPackages = parseBundleManifest(is);
        } catch (BundleException e) {
          // silently ignore bundle manifest parsing problems
        }
      }
    }
    return new ClasspathJar(file, zipFile, packageNames, exportedPackages);
  }
}
