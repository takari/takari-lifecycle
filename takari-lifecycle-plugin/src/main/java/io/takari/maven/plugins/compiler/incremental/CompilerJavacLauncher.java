package io.takari.maven.plugins.compiler.incremental;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.maven.plugins.compiler.incremental.CompilerJavacForked.CompilerConfiguration;
import io.takari.maven.plugins.compiler.incremental.CompilerJavacForked.CompilerOutput;
import io.takari.maven.plugins.compiler.incremental.CompilerJavacForked.CompilerOutputProcessor;

import java.io.File;
import java.io.IOException;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;

public class CompilerJavacLauncher {

  private final BuildContext context;

  private final AbstractCompileMojo config;

  private File jar;

  private File workingDirectory;

  public CompilerJavacLauncher(BuildContext context, AbstractCompileMojo config) {
    this.context = context;
    this.config = config;
  }

  public void compile() throws IOException {
    // use the same JVM as the one used to run Maven (the "java.home" one)
    String executable =
        System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    if (File.separatorChar == '\\') {
      executable = executable + ".exe";
    }

    File options = File.createTempFile("javac-forked", ".options");
    new CompilerConfiguration(config.getSourceEncoding(), config.getCompilerOptions(),
        config.getSources()).write(options);

    File output = File.createTempFile("javac-forked", ".output");

    CommandLine cli = new CommandLine(executable);
    cli.addArguments(new String[] {"-cp", jar.getAbsolutePath()});
    cli.addArgument(CompilerJavacForked.class.getName());
    cli.addArgument(options.getAbsolutePath());
    cli.addArgument(output.getAbsolutePath());

    DefaultExecutor executor = new DefaultExecutor();
    // ExecuteWatchdog watchdog = null;
    // if (forkedProcessTimeoutInSeconds > 0) {
    // watchdog = new ExecuteWatchdog(forkedProcessTimeoutInSeconds * 1000L);
    // executor.setWatchdog(watchdog);
    // }
    // best effort to avoid orphaned child process
    executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
    executor.setWorkingDirectory(workingDirectory);
    executor.execute(cli);

    CompilerOutput.process(output, new CompilerOutputProcessor() {
      @Override
      public void processOutput(File file) {
        context.processOutput(file);
      }

      @Override
      public void addMessage(String path, int line, int column, String message, int kind) {
        if (".".equals(path)) {

        } else {
          Input<File> input = context.registerInput(new File(path)).process();
          input.addMessage(line, column, message, kind, null);
        }
      }
    });
  }

  public void setWorkingDirectory(File workingDirectory) {
    this.workingDirectory = workingDirectory;
  }

  public void setJar(File jar) {
    this.jar = jar;
  }
}
