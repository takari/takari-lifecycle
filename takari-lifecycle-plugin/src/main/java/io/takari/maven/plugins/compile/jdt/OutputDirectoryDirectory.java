package io.takari.maven.plugins.compile.jdt;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathDirectory;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;

import java.io.File;
import java.util.Collection;

import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

class OutputDirectoryClasspathEntry implements ClasspathEntry, MutableClasspathEntry {

  private final File directory;
  private final boolean sourcepath;
  private final String sourceEncoding;

  private ClasspathDirectory delegate;

  public OutputDirectoryClasspathEntry(File directory, boolean sourcepath, String sourceEncoding) {
    this.directory = directory;
    this.sourcepath = sourcepath;
    this.sourceEncoding = sourceEncoding;

    this.delegate = new ClasspathDirectory(directory, sourcepath, sourceEncoding);
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
    this.delegate = new ClasspathDirectory(directory, sourcepath, sourceEncoding);
  }

  @Override
  public String toString() {
    return "Classpath for source directory " + directory;
  }
}
