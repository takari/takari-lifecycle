package io.tesla.maven.plugins.compiler.plexus;

import io.tesla.maven.plugins.compiler.AbstractInternalCompiler;
import io.tesla.maven.plugins.compiler.InternalCompilerConfiguration;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.compiler.CompilerResult;
import org.codehaus.plexus.compiler.javac.JavacCompiler;
import org.eclipse.tesla.incremental.FileSet.FileSetVisitor;

public class PlexusJavacCompiler extends AbstractInternalCompiler {
  public PlexusJavacCompiler(InternalCompilerConfiguration mojo) {
    super(mojo);
  }

  @Override
  public void compile() throws MojoExecutionException {
    JavacCompiler compiler = new JavacCompiler();

    CompilerConfiguration config = new CompilerConfiguration();

    config.setSourceLocations(getSourceRoots());
    config.setIncludes(getSourceIncludes());
    config.setExcludes(getSourceExcludes());
    config.setSourceFiles(getSourceFiles());

    config.setSourceVersion(getSource());
    config.setTargetVersion(getTarget());

    config.setClasspathEntries(getClasspathElements());

    config.setOutputLocation(getOutputDirectory().getAbsolutePath());

    try {
      CompilerResult result = compiler.performCompile(config);
      if (!result.isSuccess()) {

      }
    } catch (CompilerException e) {
      throw new MojoExecutionException("Fatal error compiling", e);
    }
  }

  private Set<File> getSourceFiles() {
    final Set<File> sources = new LinkedHashSet<File>();
    for (String sourceRoot : getSourceRoots()) {
      getSourceFileSet(sourceRoot).accept(new FileSetVisitor() {
        @Override
        public void onItem(File item) {
          sources.add(item);
        }
      });
    }
    return sources;
  }
}
