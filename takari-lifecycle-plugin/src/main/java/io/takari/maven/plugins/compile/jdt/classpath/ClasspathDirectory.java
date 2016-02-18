/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import static org.eclipse.jdt.internal.compiler.util.SuffixConstants.SUFFIX_STRING_class;
import static org.eclipse.jdt.internal.compiler.util.SuffixConstants.SUFFIX_STRING_java;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.osgi.framework.BundleException;

public class ClasspathDirectory extends DependencyClasspathEntry implements ClasspathEntry {

  public static class ClasspathCompilationUnit extends CompilationUnit {
    public ClasspathCompilationUnit(File file, String encoding) {
      super(null /* contents */, file.getAbsolutePath(), encoding, null /* destinationPath */, false /* ignoreOptionalProblems */);
    }
  }

  private static class MixedClasspathDirectory extends ClasspathDirectory {
    private final String encoding;

    private MixedClasspathDirectory(File directory, Set<String> packages, Collection<String> exportedPackages, Charset encoding) {
      super(directory, packages, exportedPackages);
      this.encoding = encoding != null ? encoding.name() : null;
    }

    @Override
    protected NameEnvironmentAnswer findType0(String packageName, String typeName, AccessRestriction accessRestriction) throws IOException, ClassFormatException {
      File javaFile = getFile(packageName, typeName, SUFFIX_STRING_java);
      if (javaFile != null) {
        CompilationUnit cu = new ClasspathCompilationUnit(javaFile, encoding);
        return new NameEnvironmentAnswer(cu, accessRestriction);
      }
      return super.findType0(packageName, typeName, accessRestriction);
    }
  }

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
  public NameEnvironmentAnswer findType(String packageName, String typeName, AccessRestriction accessRestriction) {
    try {
      return findType0(packageName, typeName, accessRestriction);
    } catch (ClassFormatException | IOException e) {
      // treat as if class file is missing
    }
    return null;
  }

  protected NameEnvironmentAnswer findType0(String packageName, String typeName, AccessRestriction accessRestriction) throws IOException, ClassFormatException {
    File classFile = getFile(packageName, typeName, SUFFIX_STRING_class);
    if (classFile != null) {
      return new NameEnvironmentAnswer(ClassFileReader.read(classFile, false), accessRestriction);
    }
    return null;
  }

  @Override
  public String toString() {
    return "Classpath for directory " + file;
  }

  public static ClasspathDirectory create(File directory) {
    Set<String> packages = getPackageNames(directory);
    Collection<String> exportedPackages = getExportedPackages(directory);
    return new ClasspathDirectory(directory, packages, exportedPackages);
  }

  public static DependencyClasspathEntry createMixed(DependencyClasspathEntry entry, Charset encoding) {
    if (entry instanceof MixedClasspathDirectory) {
      return entry;
    }
    if (!(entry instanceof ClasspathDirectory)) {
      return entry;
    }
    return new MixedClasspathDirectory(entry.file, entry.packageNames, entry.exportedPackages, encoding);
  }

  private static Collection<String> getExportedPackages(File directory) {
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
    return exportedPackages;
  }

  public File getFile(String packageName, String typeName, String suffix) throws IOException {
    String qualifiedFileName = packageName + "/" + typeName + suffix;
    File file = new File(this.file, qualifiedFileName).getCanonicalFile();
    if (!file.isFile()) {
      return null;
    }
    // must respect package/type name case even on case-insensitive filesystems
    if (!file.getPath().replace('\\', '/').endsWith(qualifiedFileName)) {
      return null;
    }
    return file;
  }
}
