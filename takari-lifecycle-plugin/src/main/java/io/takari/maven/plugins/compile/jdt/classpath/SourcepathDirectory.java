package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.util.Collection;

import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public class SourcepathDirectory implements ClasspathEntry {

  private final File directory;
  private final boolean sourcepath;
  private final String sourceEncoding;

  private ClasspathDirectory delegate;

  public SourcepathDirectory(File directory, boolean sourcepath, String sourceEncoding) {
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

  public void reset() {
    this.delegate = new ClasspathDirectory(directory, sourcepath, sourceEncoding);
  }

  @Override
  public String toString() {
    return "Classpath for source directory " + directory;
  }
}
