/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.javac;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import io.takari.incrementalbuild.Resource;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;
import io.takari.maven.plugins.compile.AbstractCompileMojo.AccessRulesViolation;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Debug;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;
import io.takari.maven.plugins.compile.AbstractCompiler;
import io.takari.maven.plugins.compile.CompilerBuildContext;
import io.takari.maven.plugins.compile.ProjectClasspathDigester;
import io.takari.maven.plugins.compile.jdt.CompilerJdt;

public abstract class AbstractCompilerJavac extends AbstractCompiler {

  protected static final boolean isJava8orBetter;

  static {
    boolean _isJava8orBetter = true;
    try {
      Class.forName("java.util.Base64");
    } catch (Exception e) {
      _isJava8orBetter = false;
    }
    isJava8orBetter = _isJava8orBetter;
  }

  private final ProjectClasspathDigester digester;

  private final List<ResourceMetadata<File>> sources = new ArrayList<ResourceMetadata<File>>();

  private String classpath;
  private String sourcepath = "";
  private String processorpath;

  protected AbstractCompilerJavac(CompilerBuildContext context, ProjectClasspathDigester digester) {
    super(context);
    this.digester = digester;
  }

  protected List<String> getCompilerOptions() {
    List<String> options = new ArrayList<String>();

    // output directory
    options.add("-d");
    options.add(getOutputDirectory().getAbsolutePath());

    options.add("-source");
    options.add(getSource());

    if (getTarget() != null) {
      options.add("-target");
      options.add(getTarget());
    }

    options.add("-classpath");
    options.add(classpath);

    options.add("-Xprefer:source");
    options.add("-sourcepath");
    options.add(sourcepath);

    // http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javac.html#implicit
    options.add("-implicit:none");

    switch (getProc()) {
      case only:
        options.add("-proc:only");
        break;
      case proc:
        // this is the javac default
        break;
      case none:
        options.add("-proc:none");
        break;
      default:
        throw new IllegalArgumentException();
    }
    if (getProc() != Proc.none) {
      options.add("-s");
      options.add(getGeneratedSourcesDirectory().getAbsolutePath());

      if (processorpath != null) {
        options.add("-processorpath");
        options.add(processorpath);
      }

      if (getAnnotationProcessors() != null) {
        options.add("-processor");
        StringBuilder processors = new StringBuilder();
        for (String processor : getAnnotationProcessors()) {
          if (processors.length() > 0) {
            processors.append(',');
          }
          processors.append(processor);
        }
        options.add(processors.toString());
      }

      if (getAnnotationProcessorOptions() != null) {
        for (Map.Entry<String, String> option : getAnnotationProcessorOptions().entrySet()) {
          options.add("-A" + option.getKey() + "=" + option.getValue());
        }
      }
    }

    if (isVerbose()) {
      options.add("-verbose");
    }

    if (isParameters()) {
      options.add("-parameters");
    }

    Set<Debug> debug = getDebug();
    if (debug == null || debug.contains(Debug.all)) {
      options.add("-g");
    } else if (debug.contains(Debug.none)) {
      options.add("-g:none");
    } else {
      StringBuilder keywords = new StringBuilder();
      for (Debug keyword : debug) {
        if (keywords.length() > 0) {
          keywords.append(',');
        }
        keywords.append(keyword.name());
      }
      options.add("-g:" + keywords.toString());
    }

    if (isShowWarnings()) {
      options.add("-Xlint:all");
    } else {
      options.add("-Xlint:none");
    }

    return options;
  }

  @Override
  public boolean setClasspath(List<File> dependencies, File mainClasses, Set<File> directDependencies) throws IOException {
    List<File> classpath = new ArrayList<>();
    if (mainClasses != null) {
      classpath.add(mainClasses);
    }
    classpath.addAll(dependencies);

    if (log.isDebugEnabled()) {
      StringBuilder msg = new StringBuilder();
      for (File element : classpath) {
        msg.append("\n   ").append(element);
      }
      log.debug("Compile classpath: {} entries{}", classpath.size(), msg.toString());
    }

    StringBuilder cp = new StringBuilder();
    cp.append(getOutputDirectory().getAbsolutePath());
    for (File dependency : classpath) {
      if (dependency != null) {
        cp.append(File.pathSeparatorChar).append(dependency.getAbsolutePath());
      }
    }
    this.classpath = cp.toString();

    return digester.digestClasspath(classpath);
  }

  @Override
  public boolean setSourcepath(List<File> dependencies, Set<File> sourceRoots) throws IOException {
    StringBuilder cp = new StringBuilder();
    for (File dependency : dependencies) {
      if (dependency != null) {
        cp.append(File.pathSeparatorChar).append(dependency.getAbsolutePath());
      }
    }
    this.sourcepath = cp.toString();

    return digester.digestSourcepath(dependencies);
  }

  @Override
  public boolean setSources(List<ResourceMetadata<File>> sources) {
    this.sources.addAll(sources);

    List<ResourceMetadata<File>> modifiedSources = new ArrayList<ResourceMetadata<File>>();
    List<ResourceMetadata<File>> inputs = new ArrayList<ResourceMetadata<File>>();
    for (ResourceMetadata<File> input : sources) {
      inputs.add(input);
      if (input.getStatus() != ResourceStatus.UNMODIFIED) {
        modifiedSources.add(input);
      }
    }
    Collection<ResourceMetadata<File>> deletedSources = context.getRemovedSources();

    if (!context.isEscalated() && log.isDebugEnabled()) {
      StringBuilder inputsMsg = new StringBuilder("Modified inputs:");
      for (ResourceMetadata<File> input : modifiedSources) {
        inputsMsg.append("\n   ").append(input.getStatus()).append(" ").append(input.getResource());
      }
      for (ResourceMetadata<File> input : deletedSources) {
        inputsMsg.append("\n   ").append(input.getStatus()).append(" ").append(input.getResource());
      }
      log.debug(inputsMsg.toString());
    }

    return !modifiedSources.isEmpty() || !deletedSources.isEmpty();
  }

  @Override
  public void setPrivatePackageReference(AccessRulesViolation accessRulesViolation) {
    if (accessRulesViolation == AccessRulesViolation.error) {
      String msg = String.format("Compiler %s does not support privatePackageReference=error, use compilerId=%s", getCompilerId(), CompilerJdt.ID);
      throw new IllegalArgumentException(msg);
    }
  }

  @Override
  public void setTransitiveDependencyReference(AccessRulesViolation accessRulesViolation) {
    if (accessRulesViolation == AccessRulesViolation.error) {
      String msg = String.format("Compiler %s does not support transitiveDependencyReference=error, use compilerId=%s", getCompilerId(), CompilerJdt.ID);
      throw new IllegalArgumentException(msg);
    }
  }

  @Override
  public boolean setProcessorpath(List<File> processorpath) throws IOException {
    if (processorpath != null) {
      if (log.isDebugEnabled()) {
        StringBuilder msg = new StringBuilder();
        for (File element : processorpath) {
          msg.append("\n   ").append(element);
        }
        log.debug("Processorpath: {} entries{}", processorpath.size(), msg.toString());
      }
      this.processorpath = Joiner.on(File.pathSeparatorChar).join(processorpath);
    }
    return digester.digestProcessorpath(processorpath != null ? processorpath : ImmutableList.<File>of());
  }

  @Override
  public final int compile() throws MojoExecutionException, IOException {
    // eagerly delete all outputs.
    // otherwise javac may use stale outputs and resolve types that will be deleted at the end of the build
    context.deleteOutputs();

    // everything is being rebuilt, mark as processed
    Map<File, Resource<File>> files = new LinkedHashMap<>(sources.size());
    for (ResourceMetadata<File> input : sources) {
      files.put(input.getResource(), input.process());
    }

    return compile(files);
  }

  protected abstract int compile(Map<File, Resource<File>> sources) throws MojoExecutionException, IOException;

  protected abstract String getCompilerId();

}
