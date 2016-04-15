package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.osgi.framework.BundleException;

abstract class AbstractClasspathDirectory extends DependencyClasspathEntry implements ClasspathEntry {

  protected AbstractClasspathDirectory(File directory) {
    super(directory, getPackageNames(directory), getExportedPackages(directory));
  }

  protected static Set<String> getPackageNames(File directory) {
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

  protected abstract NameEnvironmentAnswer findType0(String packageName, String typeName, AccessRestriction accessRestriction) throws IOException, ClassFormatException;

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
