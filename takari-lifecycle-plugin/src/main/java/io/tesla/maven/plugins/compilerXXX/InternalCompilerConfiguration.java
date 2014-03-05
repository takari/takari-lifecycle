package io.tesla.maven.plugins.compilerXXX;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;

// XXX do we need both InternalCompilerConfiguration and AbstractInternalCompiler?
public interface InternalCompilerConfiguration {
  File getPom();

  List<String> getSourceRoots();

  Set<String> getSourceIncludes();

  Set<String> getSourceExcludes();

  File getOutputDirectory();

  List<String> getClasspathElements();

  List<Artifact> getCompileArtifacts();

  String getSource();

  String getTarget();
}
