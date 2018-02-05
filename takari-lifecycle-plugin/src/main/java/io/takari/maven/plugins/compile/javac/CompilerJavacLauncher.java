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
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;

import io.takari.incrementalbuild.MessageSeverity;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;
import io.takari.maven.plugins.compile.CompilerBuildContext;
import io.takari.maven.plugins.compile.ProjectClasspathDigester;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerConfiguration;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerOutput;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerOutputProcessor;

@Named(CompilerJavacLauncher.ID)
public class CompilerJavacLauncher extends AbstractCompilerJavac {

  public static final String ID = "forked-javac";

  private File jar;

  private File basedir;

  private File buildDirectory;

  private String meminitial;

  private String maxmem;

  @Inject
  public CompilerJavacLauncher(CompilerBuildContext context, ProjectClasspathDigester digester) {
    super(context, digester);
  }

  @Override
  public int compile(Map<File, Resource<File>> sources) throws IOException {
    if (sources.isEmpty()) {
      return 0;
    }

    File options = File.createTempFile("javac-forked", ".options", buildDirectory);
    File output = File.createTempFile("javac-forked", ".output", buildDirectory);
    compile(options, output, sources);
    // don't delete temp files in case of an exception
    // they maybe useful to debug the problem
    options.delete();
    output.delete();

    return sources.size();
  }

  private void compile(File options, File output, final Map<File, Resource<File>> sources) throws IOException {
    new CompilerConfiguration(getSourceEncoding(), getCompilerOptions(), sources.keySet()).write(options);

    // use the same JVM as the one used to run Maven (the "java.home" one)
    String executable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    if (File.separatorChar == '\\') {
      executable = executable + ".exe";
    }

    CommandLine cli = new CommandLine(executable);

    // jvm options
    cli.addArguments(new String[] {"-cp", jar.getAbsolutePath()});
    if (meminitial != null) {
      cli.addArgument("-Xms" + meminitial);
    }
    if (maxmem != null) {
      cli.addArgument("-Xmx" + maxmem);
    }

    // main class and program arguments
    cli.addArgument(CompilerJavacForked.class.getName());
    cli.addArgument(options.getAbsolutePath(), false);
    cli.addArgument(output.getAbsolutePath(), false);

    DefaultExecutor executor = new DefaultExecutor();
    // ExecuteWatchdog watchdog = null;
    // if (forkedProcessTimeoutInSeconds > 0) {
    // watchdog = new ExecuteWatchdog(forkedProcessTimeoutInSeconds * 1000L);
    // executor.setWatchdog(watchdog);
    // }
    // best effort to avoid orphaned child process
    executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
    executor.setWorkingDirectory(basedir);

    log.debug("External java process command line:\n   {}", cli);
    try {
      executor.execute(cli); // this throws ExecuteException if process return code != 0
    } catch (ExecuteException e) {
      if (!log.isDebugEnabled()) {
        log.info("External java process command line:\n   {}", cli);
      }
      throw e;
    }

    final Map<File, Output<File>> outputs = new HashMap<File, Output<File>>();

    CompilerOutput.process(output, new CompilerOutputProcessor() {
      @Override
      public void processOutput(File inputFile, File outputFile) {
        outputs.put(outputFile, context.processOutput(outputFile));
      }

      @Override
      public void addMessage(String path, int line, int column, String message, MessageSeverity kind) {
        if (".".equals(path)) {
          context.addPomMessage(message, kind, null);
        } else {
          File file = new File(path);
          Resource<File> resource = sources.get(file);
          if (resource == null) {
            resource = outputs.get(file);
          }
          if (resource != null) {
            if (isShowWarnings() || kind != MessageSeverity.WARNING) {
              resource.addMessage(line, column, message, kind, null);
            }
          } else {
            log.warn("Unexpected java resource {}", file);
          }
        }
      }

      @Override
      public void addLogMessage(String message) {
        log.warn(message);
      }
    });
  }

  public void setBasedir(File basedir) {
    this.basedir = basedir;
  }

  public void setJar(File jar) {
    this.jar = jar;
  }

  public void setBuildDirectory(File buildDirectory) {
    this.buildDirectory = buildDirectory;
  }

  public void setMeminitial(String meminitial) {
    this.meminitial = meminitial;
  }

  public void setMaxmem(String maxmem) {
    this.maxmem = maxmem;
  }

  @Override
  protected String getCompilerId() {
    return ID;
  }
}
