package io.takari.maven.plugins.compiler.incremental;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.maven.plugin.MojoExecutionException;

public class CompilerJavac {

  private final BuildContext context;

  private final AbstractCompileMojo config;

  public CompilerJavac(BuildContext context, AbstractCompileMojo config) {
    this.context = context;
    this.config = config;
  }

  public void compile() throws MojoExecutionException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    if (compiler == null) {
      throw new MojoExecutionException("No compiler is provided in this environment. "
          + "Perhaps you are running on a JRE rather than a JDK?");
    }

    final Charset sourceEncoding = config.getSourceEncoding();
    final DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    final StandardJavaFileManager standardFileManager =
        compiler.getStandardFileManager(diagnosticCollector, null, sourceEncoding);

    final Iterable<? extends JavaFileObject> fileObjects =
        standardFileManager.getJavaFileObjectsFromFiles(config.getSources());

    Iterable<String> options = buildCompilerOptions();

    final JavaCompiler.CompilationTask task = compiler.getTask(null, // Writer out
        standardFileManager, // file manager
        diagnosticCollector, // diagnostic listener
        options, //
        null, // Iterable<String> classes to process by annotation processor(s)
        fileObjects);

    task.call();

    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticCollector.getDiagnostics()) {
      JavaFileObject source = diagnostic.getSource();
      if (source != null) {
        Input<File> input = context.registerInput(new File(source.toUri())).process();
        input.addMessage((int) diagnostic.getLineNumber(), (int) diagnostic.getColumnNumber(),
            diagnostic.getMessage(null), toSeverity(diagnostic.getKind()), null);
      } else {
        Input<File> input = context.registerInput(config.getPom()).process();
        // TODO execution line/column
        input.addMessage(0, 0, diagnostic.getMessage(null), toSeverity(diagnostic.getKind()), null);
      }
    }
  }

  private int toSeverity(Kind kind) {
    return kind == Kind.ERROR ? BuildContext.SEVERITY_ERROR : BuildContext.SEVERITY_WARNING;
  }

  private Iterable<String> buildCompilerOptions() {
    List<String> options = new ArrayList<String>();

    // output directory
    options.add("-d");
    options.add(config.getOutputDirectory().getAbsolutePath());

    options.add("-target");
    options.add(config.getTarget());

    options.add("-source");
    options.add(config.getSource());

    return options;
  }
}
