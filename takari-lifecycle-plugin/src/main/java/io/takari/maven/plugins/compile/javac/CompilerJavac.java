/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.javac;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.BuildContext.Resource;
import io.takari.incrementalbuild.BuildContext.Severity;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.maven.plugin.MojoExecutionException;

@Named(CompilerJavac.ID)
public class CompilerJavac extends AbstractCompilerJavac {

  public static final String ID = "javac";

  private static final boolean isJava7;

  static {
    boolean isJava7x = true;
    try {
      Class.forName("java.nio.file.Files");
    } catch (Exception e) {
      isJava7x = false;
    }
    isJava7 = isJava7x;
  }

  static JavaCompiler getSystemJavaCompiler() throws MojoExecutionException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new MojoExecutionException("No compiler is provided in this environment. " + "Perhaps you are running on a JRE rather than a JDK?");
    }
    return compiler;
  }

  private static interface JavaCompilerFactory {
    public JavaCompiler acquire() throws MojoExecutionException;

    public void release(JavaCompiler compiler);
  }

  private static final JavaCompilerFactory REUSECREATED = new JavaCompilerFactory() {

    // TODO broken if this plugin is loaded by multiple classloaders
    // https://cwiki.apache.org/confluence/display/MAVEN/Maven+3.x+Class+Loading
    private final Deque<JavaCompiler> compilers = new ArrayDeque<JavaCompiler>();

    @Override
    public JavaCompiler acquire() throws MojoExecutionException {
      synchronized (compilers) {
        if (!compilers.isEmpty()) {
          return compilers.removeFirst();
        }
      }
      return getSystemJavaCompiler();
    }

    @Override
    public void release(JavaCompiler compiler) {
      synchronized (compilers) {
        compilers.addFirst(compiler);
      }
    }
  };

  private static final JavaCompilerFactory SINGLETON = new JavaCompilerFactory() {

    private JavaCompiler compiler;

    @Override
    public void release(JavaCompiler compiler) {}

    @Override
    public synchronized JavaCompiler acquire() throws MojoExecutionException {
      if (compiler == null) {
        compiler = getSystemJavaCompiler();
      }
      return compiler;
    }
  };

  @Inject
  public CompilerJavac(DefaultBuildContext<?> context, ProjectClasspathDigester digester) {
    super(context, digester);
  }

  @Override
  public void compile() throws MojoExecutionException, IOException {
    // java 6 limitations
    // - there is severe performance penalty using new JavaCompiler instance
    // - the same JavaCompiler cannot be used concurrently
    // - even different JavaCompiler instances can't do annotation processing concurrently

    // java 7 (and I assume newer) do not have these limitations

    // The workaround is two-fold
    // - reuse JavaCompiler instances, but not on multiple threads
    // - do not allow in-process annotation processing

    if (!isJava7 && getProc() != Proc.none) {
      // TODO maybe allow in single-threaded mode
      throw new MojoExecutionException("Annotation processing requires forked JVM on Java 6");
    }

    final JavaCompilerFactory factory = isJava7 ? SINGLETON : REUSECREATED;

    final JavaCompiler compiler = factory.acquire();
    final StandardJavaFileManager javaFileManager = compiler.getStandardFileManager(null, null, getSourceEncoding());
    try {
      compile(compiler, javaFileManager);
    } finally {
      javaFileManager.flush();
      javaFileManager.close();
      factory.release(compiler);
    }
  }

  private void compile(JavaCompiler compiler, StandardJavaFileManager javaFileManager) throws IOException {
    context.deleteStaleOutputs(false);

    final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
    final Iterable<? extends JavaFileObject> javaSources = javaFileManager.getJavaFileObjectsFromFiles(getSourceFiles());

    final Map<File, Output<File>> looseOutputs = new HashMap<File, Output<File>>();
    final Map<File, Input<File>> inputs = new HashMap<File, Input<File>>();

    for (JavaFileObject source : javaSources) {
      File sourceFile = FileObjects.toFile(source);
      inputs.put(sourceFile, context.registerInput(sourceFile).process());
    }

    final Iterable<String> options = getCompilerOptions();
    final RecordingJavaFileManager recordingFileManager = new RecordingJavaFileManager(javaFileManager) {
      @Override
      protected void record(File inputFile, File outputFile) {
        Input<File> input = inputs.get(inputFile);
        if (input != null) {
          input.associateOutput(outputFile);
        } else {
          looseOutputs.put(outputFile, context.processOutput(outputFile));
        }
      }
    };

    Writer stdout = new PrintWriter(System.out, true);
    final JavaCompiler.CompilationTask task = compiler.getTask(stdout, // Writer out
        recordingFileManager, // file manager
        diagnosticCollector, // diagnostic listener
        options, //
        null, // Iterable<String> classes to process by annotation processor(s)
        javaSources);

    final boolean success = task.call();

    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticCollector.getDiagnostics()) {
      final JavaFileObject source = diagnostic.getSource();
      final Severity severity = toSeverity(diagnostic.getKind(), success);
      final String message = diagnostic.getMessage(null);

      if (isShowWarnings() || severity != Severity.WARNING) {
        if (source != null) {
          File file = FileObjects.toFile(source);
          if (file != null) {
            Resource<File> resource = inputs.get(file);
            if (resource == null) {
              resource = looseOutputs.get(file);
            }
            if (resource != null) {
              resource.addMessage((int) diagnostic.getLineNumber(), (int) diagnostic.getColumnNumber(), message, severity, null);
            } else {
              log.warn("Unexpected java {} resource {}", source.getKind(), source.toUri().toASCIIString());
            }
          } else {
            log.warn("Unsupported compiler message on {} resource {}: {}", source.getKind(), source.toUri(), message);
          }
        } else {
          Input<File> input = context.registerInput(getPom()).process();
          // TODO execution line/column
          input.addMessage(0, 0, message, severity, null);
        }
      }
    }
  }

  private BuildContext.Severity toSeverity(Diagnostic.Kind kind, boolean success) {
    // javac appears to report errors even when compilation was success.
    // I was only able to reproduce this with annotation processing on java 6
    // for consistency with forked mode, downgrade errors to warning here too
    if (success && kind == Kind.ERROR) {
      kind = Kind.WARNING;
    }

    BuildContext.Severity severity;
    switch (kind) {
      case ERROR:
        severity = BuildContext.Severity.ERROR;
        break;
      case NOTE:
        severity = BuildContext.Severity.INFO;
        break;
      default:
        severity = BuildContext.Severity.WARNING;
        break;
    }

    return severity;
  }

  @Override
  protected String getCompilerId() {
    return ID;
  }
}
