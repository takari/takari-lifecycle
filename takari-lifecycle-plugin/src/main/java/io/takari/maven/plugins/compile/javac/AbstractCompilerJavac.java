package io.takari.maven.plugins.compile.javac;

import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.maven.plugins.compile.AbstractCompileMojo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;

public class AbstractCompilerJavac {

  protected final DefaultBuildContext<?> context;

  protected final AbstractCompileMojo config;

  protected AbstractCompilerJavac(DefaultBuildContext<?> context, AbstractCompileMojo config) {
    this.context = context;
    this.config = config;
  }

  protected List<String> getCompilerOptions() {
    List<String> options = new ArrayList<String>();

    // output directory
    options.add("-d");
    options.add(config.getOutputDirectory().getAbsolutePath());

    options.add("-source");
    options.add(config.getSource());

    if (config.getTarget() != null) {
      options.add("-target");
      options.add(config.getTarget());
    }

    options.add("-classpath");
    options.add(getClasspath());

    switch (config.getProc()) {
      case only:
        options.add("-proc:only");
        break;
      case proc:
        // this is the javac default
        break;
      case none:
        options.add("-proc:none");
        break;
    }
    if (config.isAnnotationProcessing()) {
      options.add("-s");
      options.add(config.getGeneratedSourcesDirectory().getAbsolutePath());

      if (config.getAnnotationProcessors() != null) {
        options.add("-processor");
        StringBuilder processors = new StringBuilder();
        for (String processor : config.getAnnotationProcessors()) {
          if (processors.length() > 0) {
            processors.append(',');
          }
          processors.append(processor);
        }
        options.add(processors.toString());
      }
    }

    if (config.isVerbose()) {
      options.add("-verbose");
    }

    return options;
  }

  private String getClasspath() {
    StringBuilder cp = new StringBuilder();
    cp.append(config.getOutputDirectory().getAbsolutePath());
    for (Artifact cpe : config.getCompileArtifacts()) {
      File file = cpe.getFile();
      if (file != null) {
        if (cp.length() > 0) {
          cp.append(File.pathSeparatorChar);
        }
        cp.append(file.getAbsolutePath());
      }
    }
    return cp.toString();
  }

}
