/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.javac;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import io.takari.incrementalbuild.MessageSeverity;

public class CompilerJavacForked {

  private static final String EOL = "\n";

  private static final String ENCODING = "UTF-8";

  public static class CompilerConfiguration {

    private final Charset encoding;

    private final Iterable<String> options;

    private final Iterable<File> sources;

    public CompilerConfiguration(Charset encoding, Iterable<String> options, Iterable<File> sources) {
      this.encoding = encoding;
      this.options = options;
      this.sources = sources;
    }

    public Charset getSourceEncoding() {
      return encoding;
    }

    public Iterable<String> getCompilerOptions() {
      return options;
    }

    public Iterable<File> getSources() {
      return sources;
    }

    public void write(File file) throws IOException {
      Writer writer = newWriter(file);
      try {
        // encoding
        if (encoding != null) {
          writer.write('C');
          writer.write(encoding.name());
          writer.write(EOL);
        }

        // options
        for (String option : options) {
          writer.write('O');
          writer.write(option);
          writer.write(EOL);
        }

        // sources
        for (File source : sources) {
          writer.write('S');
          writer.write(source.getCanonicalPath());
          writer.write(EOL);
        }
      } finally {
        writer.close();
      }
    }

    public static CompilerConfiguration read(File file) throws IOException {
      Charset encoding = null;
      List<String> options = new ArrayList<String>();
      List<File> sources = new ArrayList<File>();

      BufferedReader reader = newBufferedReader(file);
      try {
        String str;
        while ((str = reader.readLine()) != null) {
          String value = str.substring(1);
          switch (str.charAt(0)) {
            case 'C':
              encoding = Charset.forName(value);
              break;
            case 'O':
              options.add(value);
              break;
            case 'S':
              sources.add(new File(value));
              break;
          }
        }
      } finally {
        reader.close();
      }

      return new CompilerConfiguration(encoding, options, sources);
    }
  }

  public static class CompilerOutput {

    private final Writer writer;

    public CompilerOutput(File file) throws IOException {
      this.writer = newWriter(file);
    }

    public void processOutput(File inputFile, File outputFile) {
      try {
        writer.write('O');
        writer.write(inputFile != null ? URLEncoder.encode(inputFile.getCanonicalPath(), ENCODING) : ".");
        writer.write(' ');
        writer.write(URLEncoder.encode(outputFile.getCanonicalPath(), ENCODING));
        writer.write(EOL);
      } catch (IOException e) {
        handleException(e);
      }
    }

    public void addMessage(String path, int line, int column, String message, Kind kind) {
      try {
        writer.write('M');
        writer.write(URLEncoder.encode(path, ENCODING));
        writer.write(' ');
        writer.write(Integer.toString(line));
        writer.write(' ');
        writer.write(Integer.toString(column));
        writer.write(' ');
        switch (kind) {
          case ERROR:
            writer.write('E');
            break;
          case NOTE:
            writer.write('I');
            break;
          default:
            writer.write('W');
            break;
        }
        writer.write(' ');
        writer.write(URLEncoder.encode(message, ENCODING));
        writer.write(EOL);
      } catch (IOException e) {
        handleException(e);
      }
    }

    public void addLogMessage(String message) {
      try {
        writer.write('L');
        writer.write(message);
        writer.write(EOL);
      } catch (IOException e) {
        handleException(e);
      }
    }

    public void close() throws IOException {
      writer.close();
    }

    private void handleException(IOException e) {
      e.printStackTrace();
      System.exit(1); // this will trigger ExecutionException in Maven plugin
    }

    public static void process(File file, CompilerOutputProcessor callback) throws IOException {
      BufferedReader reader = newBufferedReader(file);
      try {
        String str;
        while ((str = reader.readLine()) != null) {
          String value = str.substring(1);
          switch (str.charAt(0)) {
            case 'O': {
              StringTokenizer st = new StringTokenizer(value, " ");
              String inputPath = URLDecoder.decode(st.nextToken(), ENCODING);
              String outputPath = URLDecoder.decode(st.nextToken(), ENCODING);
              callback.processOutput(!".".equals(inputPath) ? new File(inputPath) : null, new File(outputPath));
              break;
            }
            case 'M': {
              StringTokenizer st = new StringTokenizer(value, " ");
              String path = URLDecoder.decode(st.nextToken(), ENCODING);
              int line = Integer.parseInt(st.nextToken());
              int column = Integer.parseInt(st.nextToken());
              MessageSeverity severity = toSeverity(st.nextToken());
              String message = URLDecoder.decode(st.nextToken(), ENCODING);
              callback.addMessage(path, line, column, message, severity);
              break;
            }
            case 'L': {
              callback.addLogMessage(value);
              break;
            }
            default:
              throw new IllegalArgumentException();
          }
        }
      } finally {
        reader.close();
      }
    }

    private static MessageSeverity toSeverity(String token) {
      switch (token) {
        case "E":
          return MessageSeverity.ERROR;
        case "I":
          return MessageSeverity.INFO;
        default:
          return MessageSeverity.WARNING;
      }
    }
  }

  public static interface CompilerOutputProcessor {
    public void processOutput(File inputFile, File outputFile);

    public void addMessage(String path, int line, int column, String message, MessageSeverity kind);

    public void addLogMessage(String message);
  }

  static Writer newWriter(File file) throws IOException {
    return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), ENCODING));
  }

  static BufferedReader newBufferedReader(File file) throws IOException {
    return new BufferedReader(new InputStreamReader(new FileInputStream(file), ENCODING));
  }

  public static void main(String[] args) throws IOException {
    final CompilerConfiguration config = CompilerConfiguration.read(new File(args[0]));
    final CompilerOutput output = new CompilerOutput(new File(args[1]));
    try {
      compile(config, output);
    } finally {
      output.close();
    }
  }

  private static void compile(final CompilerConfiguration config, final CompilerOutput output) {

    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      output.addMessage(".", 0, 0, "No compiler is provided in this environment. " + "Perhaps you are running on a JRE rather than a JDK?", Kind.ERROR);
      return;
    }

    final Charset sourceEncoding = config.getSourceEncoding();
    final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();
    final StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnosticCollector, null, sourceEncoding);
    final Iterable<? extends JavaFileObject> fileObjects = standardFileManager.getJavaFileObjectsFromFiles(config.getSources());
    final Iterable<String> options = config.getCompilerOptions();
    final RecordingJavaFileManager recordingFileManager = new RecordingJavaFileManager(standardFileManager, sourceEncoding) {
      @Override
      protected void record(File inputFile, File outputFile) {
        output.processOutput(inputFile, outputFile);
      }
    };

    Writer stdout = new PrintWriter(System.out, true);
    final JavaCompiler.CompilationTask task = compiler.getTask(stdout, // Writer out
        recordingFileManager, // file manager
        diagnosticCollector, // diagnostic listener
        options, //
        null, // Iterable<String> classes to process by annotation processor(s)
        fileObjects);

    boolean success = task.call();

    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticCollector.getDiagnostics()) {
      JavaFileObject source = diagnostic.getSource();

      // when doing annotation processing, javac 6 reports errors when handwritten sources
      // depend on generated sources even when overall compilation is reported as success
      // to prevent false build failures, never issue ERROR messages after successful compilation
      Kind kind = diagnostic.getKind();
      if (success && kind == Kind.ERROR) {
        kind = Kind.WARNING;
      }

      if (source != null) {
        File file = FileObjects.toFile(source);
        if (file != null) {
          output.addMessage(file.getAbsolutePath(), (int) diagnostic.getLineNumber(), (int) diagnostic.getColumnNumber(), diagnostic.getMessage(null), kind);
        } else {
          output.addLogMessage(String.format("Unsupported compiler message on %s resource %s: %s", source.getKind(), source.toUri(), diagnostic.getMessage(null)));
        }
      } else {
        output.addMessage(".", 0, 0, diagnostic.getMessage(null), kind);
      }
    }
  }
}
