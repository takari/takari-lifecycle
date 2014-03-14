package io.takari.maven.plugins.compile.javac;

import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.OutputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInputMetadata;
import io.takari.incrementalbuild.spi.DefaultOutputMetadata;
import io.takari.maven.plugins.compile.AbstractCompileMojo;
import io.takari.maven.plugins.compile.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;

public abstract class AbstractCompilerJavac extends AbstractCompiler {

  private final ProjectClasspathDigester digester;

  protected final List<File> sources = new ArrayList<File>();

  protected AbstractCompilerJavac(DefaultBuildContext<?> context, AbstractCompileMojo config,
      ProjectClasspathDigester digester) {
    super(context, config);
    this.digester = digester;
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

  @Override
  public boolean setClasspath(List<Artifact> dependencies) throws IOException {
    return digester.digestDependencies(dependencies);
  }

  @Override
  public boolean setSources(List<File> sources) {
    this.sources.addAll(sources);

    // always register pom.xml. pom.xml is used to track message general compiler messages
    // if not registered, it will cause these messages to be lost during no-change rebuild
    context.registerInput(config.getPom());

    List<InputMetadata<File>> modifiedSources = new ArrayList<InputMetadata<File>>();
    List<InputMetadata<File>> inputs = new ArrayList<InputMetadata<File>>();
    for (InputMetadata<File> input : context.registerInputs(sources)) {
      inputs.add(input);
      if (input.getStatus() != ResourceStatus.UNMODIFIED) {
        modifiedSources.add(input);
      }
    }
    Set<DefaultInputMetadata<File>> deletedSources = context.getRemovedInputs(File.class);

    Set<DefaultOutputMetadata> modifiedOutputs = new HashSet<DefaultOutputMetadata>();
    for (DefaultOutputMetadata output : context.getProcessedOutputs()) {
      ResourceStatus status = output.getStatus();
      if (status == ResourceStatus.MODIFIED || status == ResourceStatus.REMOVED) {
        modifiedOutputs.add(output);
      }
    }

    if (!context.isEscalated() && log.isDebugEnabled()) {
      StringBuilder inputsMsg = new StringBuilder("Modified inputs:");
      for (InputMetadata<File> input : modifiedSources) {
        inputsMsg.append("\n   ").append(input.getStatus()).append(" ").append(input.getResource());
      }
      for (InputMetadata<File> input : deletedSources) {
        inputsMsg.append("\n   ").append(input.getStatus()).append(" ").append(input.getResource());
      }
      log.debug(inputsMsg.toString());

      if (!modifiedOutputs.isEmpty()) {
        StringBuilder outputsMsg = new StringBuilder("Modified outputs:");
        for (OutputMetadata<File> output : modifiedOutputs) {
          outputsMsg.append("\n   ").append(output.getStatus()).append(" ")
              .append(output.getResource());
        }
        log.debug(outputsMsg.toString());
      }
    }

    return !modifiedSources.isEmpty() || !deletedSources.isEmpty() || !modifiedOutputs.isEmpty();
  }

  @Override
  public void skipCompilation() {
    // javac does not track input/output association
    // need to manually carry-over output metadata
    // otherwise outouts are deleted during BuildContext#commit
    for (OutputMetadata<File> output : context.getProcessedOutputs()) {
      context.carryOverOutput(output.getResource());
    }
  }
}
