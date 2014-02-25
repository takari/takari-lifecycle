package io.takari.maven.plugins.compiler.javac;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import io.takari.maven.plugins.compiler.api.CompilerConfiguration;
import io.takari.maven.plugins.compiler.api.CompilerException;
import io.takari.maven.plugins.compiler.api.CompilerMessage;
import io.takari.maven.plugins.compiler.api.CompilerResult;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Olivier Lamy
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @since 2.0
 */
public class JavaxToolsCompiler {
  /**
   * is that thread safe ???
   */
  static final JavaCompiler COMPILER = ToolProvider.getSystemJavaCompiler();

  private static List<JavaCompiler> JAVA_COMPILERS = new CopyOnWriteArrayList<JavaCompiler>();

  protected static JavaCompiler getJavaCompiler(CompilerConfiguration compilerConfiguration) {
    switch (compilerConfiguration.getCompilerReuseStrategy()) {
    case AlwaysNew:
      return ToolProvider.getSystemJavaCompiler();
    case ReuseCreated:
      JavaCompiler javaCompiler;
      synchronized (JAVA_COMPILERS) {
        if (JAVA_COMPILERS.size() > 0) {
          javaCompiler = JAVA_COMPILERS.get(0);
          JAVA_COMPILERS.remove(javaCompiler);
          return javaCompiler;
        }
      }
      javaCompiler = ToolProvider.getSystemJavaCompiler();
      return javaCompiler;
    case ReuseSame:
    default:
      return COMPILER;
    }

  }

  static void releaseJavaCompiler(JavaCompiler javaCompiler, CompilerConfiguration compilerConfiguration) {
    if (javaCompiler == null) {
      return;
    }
    if (compilerConfiguration.getCompilerReuseStrategy() == CompilerConfiguration.CompilerReuseStrategy.ReuseCreated) {
      JAVA_COMPILERS.add(javaCompiler);
    }
  }

  static CompilerResult compileInProcess(String[] args, final CompilerConfiguration config, String[] sourceFiles) throws CompilerException {
    JavaCompiler compiler = getJavaCompiler(config);
    try {
      if (compiler == null) {
        CompilerMessage message = new CompilerMessage("No compiler is provided in this environment. " + "Perhaps you are running on a JRE rather than a JDK?", CompilerMessage.Kind.ERROR);
        return new CompilerResult(false, Collections.singletonList(message));
      }
      final String sourceEncoding = config.getSourceEncoding();
      final Charset sourceCharset = sourceEncoding == null ? null : Charset.forName(sourceEncoding);
      final DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<JavaFileObject>();
      final StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(collector, null, sourceCharset);

      final Iterable<? extends JavaFileObject> fileObjects = standardFileManager.getJavaFileObjectsFromStrings(Arrays.asList(sourceFiles));

      /*
       * (Writer out, JavaFileManager fileManager, DiagnosticListener<? super JavaFileObject> diagnosticListener, Iterable<String> options, Iterable<String> classes, Iterable<? extends JavaFileObject>
       * compilationUnits)
       */

      List<String> arguments = Arrays.asList(args);

      final JavaCompiler.CompilationTask task = compiler.getTask(null, standardFileManager, collector, arguments, null, fileObjects);
      final Boolean result = task.call();
      final ArrayList<CompilerMessage> compilerMsgs = new ArrayList<CompilerMessage>();
      for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
        CompilerMessage.Kind kind = convertKind(diagnostic);
        String baseMessage = diagnostic.getMessage(null);
        if (baseMessage == null) {
          continue;
        }
        JavaFileObject source = diagnostic.getSource();
        String longFileName = source == null ? null : source.toUri().getPath();
        String shortFileName = source == null ? null : source.getName();
        String formattedMessage = baseMessage;
        int lineNumber = Math.max(0, (int) diagnostic.getLineNumber());
        int columnNumber = Math.max(0, (int) diagnostic.getColumnNumber());
        if (source != null && lineNumber > 0) {
          // Some compilers like to copy the file name into the message, which makes it appear twice.
          String possibleTrimming = longFileName + ":" + lineNumber + ": ";
          if (formattedMessage.startsWith(possibleTrimming)) {
            formattedMessage = formattedMessage.substring(possibleTrimming.length());
          } else {
            possibleTrimming = shortFileName + ":" + lineNumber + ": ";
            if (formattedMessage.startsWith(possibleTrimming)) {
              formattedMessage = formattedMessage.substring(possibleTrimming.length());
            }
          }
        }
        compilerMsgs.add(new CompilerMessage(longFileName, kind, lineNumber, columnNumber, lineNumber, columnNumber, formattedMessage));
      }
      if (result != Boolean.TRUE && compilerMsgs.isEmpty()) {
        compilerMsgs.add(new CompilerMessage("An unknown compilation problem occurred", CompilerMessage.Kind.ERROR));
      }

      return new CompilerResult(result, compilerMsgs);
    } catch (Exception e) {
      throw new CompilerException(e.getMessage(), e);
    } finally {
      releaseJavaCompiler(compiler, config);

    }
  }

  public static CompilerMessage.Kind convertKind(Diagnostic<? extends JavaFileObject> diagnostic) {
    CompilerMessage.Kind kind;
    switch (diagnostic.getKind()) {
    case ERROR:
      kind = CompilerMessage.Kind.ERROR;
      break;
    case WARNING:
      kind = CompilerMessage.Kind.WARNING;
      break;
    case MANDATORY_WARNING:
      kind = CompilerMessage.Kind.MANDATORY_WARNING;
      break;
    case NOTE:
      kind = CompilerMessage.Kind.NOTE;
      break;
    default:
      kind = CompilerMessage.Kind.OTHER;
      break;
    }
    return kind;
  }
}
