package io.tesla.maven.plugins.compiler;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.DirectoryScanner;

public abstract class AbstractInternalCompiler {
  private final InternalCompilerConfiguration mojo;

  protected AbstractInternalCompiler(InternalCompilerConfiguration mojo) {
    this.mojo = mojo;
  }

  protected final List<String> getSourceRoots() {
    return mojo.getSourceRoots();
  }

  protected final Set<String> getSourceIncludes() {
    return mojo.getSourceIncludes();
  }

  protected final Set<String> getSourceExcludes() {
    return mojo.getSourceExcludes();
  }

  protected final File getOutputDirectory() {
    return mojo.getOutputDirectory();
  }

  protected final List<String> getClasspathElements() {
    return mojo.getClasspathElements();
  }

  protected final String getSource() {
    return mojo.getSource();
  }

  protected final String getTarget() {
    return mojo.getTarget();
  }

  protected final Set<File> getSourceFileSet(String sourceRoot) {
    File basedir = new File(sourceRoot);
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(basedir);
    scanner.setIncludes(null);
    Set<String> includes = getSourceIncludes();
    Set<String> excludes = getSourceExcludes();
    if (includes.isEmpty() && excludes.isEmpty()) {
      scanner.setIncludes(new String[] {"**/*.java"});
    } else {
      scanner.setIncludes(toArray(includes));
      scanner.setExcludes(toArray(excludes));
    }
    scanner.scan();
    Set<File> result = new LinkedHashSet<File>();
    for (String path : scanner.getIncludedFiles()) {
      result.add(new File(basedir, path));
    }
    return result;
  }

  private String[] toArray(Collection<String> collection) {
    return collection.toArray(new String[collection.size()]);
  }

  public abstract void compile() throws MojoExecutionException;
}
