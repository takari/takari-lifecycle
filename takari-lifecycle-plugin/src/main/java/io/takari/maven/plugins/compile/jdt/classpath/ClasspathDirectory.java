package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public class ClasspathDirectory implements ClasspathEntry {

  private final File directory;
  private final boolean sourcepath;
  private final String sourceEncoding;

  private final Set<String> packageNames;

  public ClasspathDirectory(File directory) {
    this(directory, false, null);
  }

  public ClasspathDirectory(File directory, boolean sourcepath, String sourceEncoding) {
    this.sourcepath = sourcepath;
    this.sourceEncoding = sourceEncoding;
    try {
      directory = directory.getCanonicalFile();
    } catch (IOException e) {
      // should not happen as we know that the file exists
      directory = directory.getAbsoluteFile();
    }
    this.directory = directory;
    this.packageNames = Collections.unmodifiableSet(initializePackageNames(directory));
  }

  private static Set<String> initializePackageNames(File directory) {
    Set<String> packages = new HashSet<String>();
    initializePackageCache(packages, directory, "");
    return packages;
  }

  private static void initializePackageCache(Set<String> packages, File directory,
      String packageName) {
    if (!packageName.isEmpty()) {
      packages.add(packageName);
    }
    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          initializePackageCache(packages, file, childPackageName(packageName, file.getName()));
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

  private String toSourceFileName(String binaryFileName) {
    return binaryFileName.substring(0, binaryFileName.length() - ".class".length()) + ".java";
  }

  @Override
  public NameEnvironmentAnswer findType(String packageName, String binaryFileName) {
    if (!packageNames.contains(packageName)) {
      return null;
    }
    if (sourcepath) {
      String qualifiedFileName = packageName + "/" + toSourceFileName(binaryFileName);
      try {
        File sourceFile = new File(directory, qualifiedFileName).getCanonicalFile();
        if (sourceFile.isFile() && matchQualifiedName(sourceFile, qualifiedFileName)) {
          CompilationUnit unit =
              new CompilationUnit(null, sourceFile.getAbsolutePath(), sourceEncoding);
          return new NameEnvironmentAnswer(unit, null);
        }
      } catch (IOException e) {
        // treat as if source file is missing
      }
    } else {
      try {
        String qualifiedFileName = packageName + "/" + binaryFileName;
        File classFile = new File(directory, qualifiedFileName).getCanonicalFile();
        if (classFile.isFile() && matchQualifiedName(classFile, qualifiedFileName)) {
          ClassFileReader reader = ClassFileReader.read(classFile, false);
          if (reader != null) {
            return new NameEnvironmentAnswer(reader, null);
          }
        }
      } catch (ClassFormatException e) {
        // treat as if class file is missing
      } catch (IOException e) {
        // treat as if class file is missing
      }
    }
    return null;
  }

  private boolean matchQualifiedName(File file, String qualifiedName) {
    return file.getAbsolutePath().replace('\\', '/').endsWith(qualifiedName);
  }

  @Override
  public String toString() {
    return "Classpath for directory " + directory;
  }
}
