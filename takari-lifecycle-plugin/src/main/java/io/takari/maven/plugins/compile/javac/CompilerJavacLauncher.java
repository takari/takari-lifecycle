package io.takari.maven.plugins.compile.javac;

import io.takari.incrementalbuild.*;
import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.OutputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.maven.plugins.compile.AbstractCompileMojo;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerConfiguration;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerOutput;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerOutputProcessor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.exec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

public class CompilerJavacLauncher {

  private Logger log = LoggerFactory.getLogger(getClass());

  private final DefaultBuildContext<?> context;

  private final AbstractCompileMojo config;

  private File jar;

  private File basedir;

  private File buildDirectory;

  public CompilerJavacLauncher(DefaultBuildContext<?> context, AbstractCompileMojo config) {
    this.context = context;
    this.config = config;
  }

  public int compile(List<File> sources) throws IOException {
    File options = File.createTempFile("javac-forked", ".options", buildDirectory);
    File output = File.createTempFile("javac-forked", ".output", buildDirectory);
    try {
      return compile(options, output, sources);
    } finally {
      options.delete();
      output.delete();
    }
  }

  private int compile(File options, File output, List<File> sourcesFiles) throws IOException {
    List<File> sources = new ArrayList<File>();

    boolean unmodified = true;
    for (InputMetadata<File> source : context.registerInputs(sourcesFiles)) {
      unmodified = unmodified && source.getStatus() == ResourceStatus.UNMODIFIED;
      sources.add(source.getResource());
    }

    boolean deleted = context.getRemovedInputs(File.class).iterator().hasNext();

    if (unmodified && !deleted && config.getChangedDependencyTypes().isEmpty()) {
      // mark outputs as up-to-date, otherwise they are deleted during BuildContext#commit
      for (OutputMetadata<File> outputMetadata : context.getProcessedOutputs()) {
        context.carryOverOutput(outputMetadata.getResource());
      }
      return 0;
    }

    new CompilerConfiguration(config.getSourceEncoding(), config.getCompilerOptions(), sources)
        .write(options);

    // use the same JVM as the one used to run Maven (the "java.home" one)
    String executable =
        System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    if (File.separatorChar == '\\') {
      executable = executable + ".exe";
    }

    CommandLine cli = new CommandLine(executable);

    // jvm options
    cli.addArguments(new String[] {"-cp", jar.getAbsolutePath()});
    if (config.getMeminitial() != null) {
      cli.addArgument("-Xms" + config.getMeminitial());
    }
    if (config.getMaxmem() != null) {
      cli.addArgument("-Xmx" + config.getMaxmem());
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

    Stopwatch stopwatch = new Stopwatch().start();
    executor.execute(cli); // this throws ExecuteException if process return code != 0
    log.info("Compilation time {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));

    for (File source : sources) {
      context.registerInput(source).process();
    }

    CompilerOutput.process(output, new CompilerOutputProcessor() {
      @Override
      public void processOutput(File file) {
        context.processOutput(file);
      }

      @Override
      public void addMessage(String path, int line, int column, String message,
          BuildContext.Severity kind) {
        if (".".equals(path)) {
          // TODO
        } else {
          Input<File> input = context.registerInput(new File(path)).process();
          input.addMessage(line, column, message, kind, null);
        }
      }
    });

    return sources.size();
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
}
