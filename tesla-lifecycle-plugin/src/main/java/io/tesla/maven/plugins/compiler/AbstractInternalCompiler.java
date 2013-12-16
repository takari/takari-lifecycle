package io.tesla.maven.plugins.compiler;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.tesla.incremental.FileSet;
import org.eclipse.tesla.incremental.FileSetBuilder;

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

  protected final FileSet getSourceFileSet(String sourceRoot) {
    final FileSetBuilder builder = new FileSetBuilder(new File(sourceRoot));
    Set<String> includes = getSourceIncludes();
    Set<String> excludes = getSourceExcludes();
    if (includes.isEmpty() && excludes.isEmpty()) {
      builder.addIncludes("**/*.java");
    } else {
      builder.addIncludes(includes).addExcludes(excludes);
    }
    return builder.build();
  }

  public abstract void compile() throws MojoExecutionException;
}
