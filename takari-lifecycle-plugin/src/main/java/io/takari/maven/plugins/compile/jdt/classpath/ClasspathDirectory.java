/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.osgi.framework.BundleException;

public class ClasspathDirectory extends DependencyClasspathEntry implements ClasspathEntry {

  private ClasspathDirectory(File directory, Set<String> packageNames, Collection<String> exportedPackages) {
    super(directory, packageNames, exportedPackages);
    try {
      directory = directory.getCanonicalFile();
    } catch (IOException e) {
      // should not happen as we know that the file exists
      directory = directory.getAbsoluteFile();
    }
  }

  private static Set<String> getPackageNames(File directory) {
    Set<String> packages = new HashSet<String>();
    populatePackageNames(packages, directory, "");
    return packages;
  }

  private static void populatePackageNames(Set<String> packageNames, File directory, String packageName) {
    if (!packageName.isEmpty()) {
      packageNames.add(packageName);
    }
    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          populatePackageNames(packageNames, file, childPackageName(packageName, file.getName()));
        }
      }
    }
  }

  private static String childPackageName(String packageName, String childName) {
    return packageName.isEmpty() ? childName : packageName + "/" + childName;
  }

  @Override
  public Collection<String> getPackageNames() {
    return packageNames;
  }

  @Override
  public NameEnvironmentAnswer findType(String packageName, String binaryFileName, AccessRestriction accessRestriction) {
    try {
      String qualifiedFileName = packageName + "/" + binaryFileName;
      File classFile = new File(file, qualifiedFileName).getCanonicalFile();
      if (classFile.isFile() && matchQualifiedName(classFile, qualifiedFileName)) {
        ClassFileReader reader = ClassFileReader.read(classFile, false);
        if (reader != null) {
          return new NameEnvironmentAnswer(reader, accessRestriction);
        }
      }
    } catch (ClassFormatException e) {
      // treat as if class file is missing
    } catch (IOException e) {
      // treat as if class file is missing
    }
    return null;
  }

  private boolean matchQualifiedName(File file, String qualifiedName) {
    return file.getAbsolutePath().replace('\\', '/').endsWith(qualifiedName);
  }

  @Override
  public String toString() {
    return "Classpath for directory " + file;
  }

  public static ClasspathDirectory create(File directory) {
    Set<String> packages = getPackageNames(directory);

    Collection<String> exportedPackages = null;
    try (InputStream is = new FileInputStream(new File(directory, PATH_EXPORT_PACKAGE))) {
      exportedPackages = parseExportPackage(is);
    } catch (IOException e) {
      // silently ignore missing/bad export-package files
    }
    if (exportedPackages == null) {
      try (InputStream is = new FileInputStream(new File(directory, PATH_MANIFESTMF))) {
        exportedPackages = parseBundleManifest(is);
      } catch (IOException | BundleException e) {
        // silently ignore missing/bad export-package files
      }
    }

    return new ClasspathDirectory(directory, packages, exportedPackages);
  }

  public File getFile(String packageName, String binaryFileName) throws IOException {
    String qualifiedFileName = packageName + "/" + binaryFileName;
    return new File(file, qualifiedFileName).getCanonicalFile();
  }
}
