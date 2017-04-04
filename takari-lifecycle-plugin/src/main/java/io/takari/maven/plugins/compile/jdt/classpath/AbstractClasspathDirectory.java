package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.osgi.framework.BundleException;

import com.google.common.collect.ImmutableMap;

abstract class AbstractClasspathDirectory extends DependencyClasspathEntry implements ClasspathEntry {

  private final Map<String, File> files;

  protected AbstractClasspathDirectory(File directory, Set<String> packages, Map<String, File> files) {
    super(directory, packages, getExportedPackages(directory));
    this.files = ImmutableMap.copyOf(files);
  }

  protected static void scanDirectory(File directory, String suffix, Set<String> packages, Map<String, File> files) {
    try {
      Path basedir = directory.toPath();
      Files.walkFileTree(basedir, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
          String relpath = basedir.relativize(dir).toString();
          if (!relpath.isEmpty()) {
            packages.add(relpath.replace('\\', '/'));
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          String relpath = basedir.relativize(file).toString();
          if (relpath.endsWith(suffix)) {
            files.put(relpath.substring(0, relpath.length() - suffix.length()).replace('\\', '/'), file.toFile());
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (NoSuchFileException expected) {
      // the directory does not exist, nothing to be alarmed about
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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

  public File getFile(String packageName, String typeName) throws IOException {
    String qualifiedFileName = packageName + "/" + typeName;
    return files.get(qualifiedFileName);
  }

}
