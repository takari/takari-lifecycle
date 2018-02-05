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
import java.io.PrintWriter;
import java.io.Writer;
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

import io.takari.incrementalbuild.MessageSeverity;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;
import io.takari.maven.plugins.compile.CompilerBuildContext;
import io.takari.maven.plugins.compile.ProjectClasspathDigester;

@Named(CompilerJavac.ID)
public class CompilerJavac extends AbstractCompilerJavac {

  public static final String ID = "javac";

  private static JavaCompiler compiler;

  static synchronized JavaCompiler getSystemJavaCompiler() throws MojoExecutionException {
    if (compiler == null) {
      compiler = ToolProvider.getSystemJavaCompiler();
    }
    if (compiler == null) {
      throw new MojoExecutionException("No compiler is provided in this environment. " + "Perhaps you are running on a JRE rather than a JDK?");
    }
    return compiler;
  }

  @Inject
  public CompilerJavac(CompilerBuildContext context, ProjectClasspathDigester digester) {
    super(context, digester);
  }

  @Override
  public int compile(Map<File, Resource<File>> sources) throws MojoExecutionException, IOException {
    if (sources.isEmpty()) {
      return 0;
    }

    final JavaCompiler compiler = getSystemJavaCompiler();
    final StandardJavaFileManager javaFileManager = compiler.getStandardFileManager(null, null, getSourceEncoding());
    try {
      compile(compiler, javaFileManager, sources);
    } finally {
      javaFileManager.flush();
      javaFileManager.close();
    }
    return sources.size();
  }

  private void compile(JavaCompiler compiler, StandardJavaFileManager javaFileManager, Map<File, Resource<File>> sources) throws IOException {
    final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
    final Iterable<? extends JavaFileObject> javaSources = javaFileManager.getJavaFileObjectsFromFiles(sources.keySet());

    final Map<File, Output<File>> outputs = new HashMap<File, Output<File>>();

    final Iterable<String> options = getCompilerOptions();
    final RecordingJavaFileManager recordingFileManager = new RecordingJavaFileManager(javaFileManager, getSourceEncoding()) {
      @Override
      protected void record(File inputFile, File outputFile) {
        outputs.put(outputFile, context.processOutput(outputFile));
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
      final MessageSeverity severity = toSeverity(diagnostic.getKind(), success);
      final String message = diagnostic.getMessage(null);

      if (isShowWarnings() || severity != MessageSeverity.WARNING) {
        if (source != null) {
          File file = FileObjects.toFile(source);
          if (file != null) {
            Resource<File> resource = sources.get(file);
            if (resource == null) {
              resource = outputs.get(file);
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
          context.addPomMessage(message, severity, null);
        }
      }
    }
  }

  private MessageSeverity toSeverity(Diagnostic.Kind kind, boolean success) {
    // javac appears to report errors even when compilation was success.
    // I was only able to reproduce this with annotation processing on java 6
    // for consistency with forked mode, downgrade errors to warning here too
    if (success && kind == Kind.ERROR) {
      kind = Kind.WARNING;
    }

    MessageSeverity severity;
    switch (kind) {
      case ERROR:
        severity = MessageSeverity.ERROR;
        break;
      case NOTE:
        severity = MessageSeverity.INFO;
        break;
      default:
        severity = MessageSeverity.WARNING;
        break;
    }

    return severity;
  }

  @Override
  protected String getCompilerId() {
    return ID;
  }
}
