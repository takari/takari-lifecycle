package io.takari.maven.plugins.compile.jdt;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import javax.tools.StandardJavaFileManager;

import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;

import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;
import io.takari.maven.plugins.compile.CompilerBuildContext;

// TODO reconcile with BatchProcessingEnvImpl
class ProcessingEnvImpl extends BaseProcessingEnvImpl {

  public ProcessingEnvImpl(CompilerBuildContext context, StandardJavaFileManager fileManager, Map<String, String> processorOptions, Compiler compiler, CompilerJdt incrementalCompiler, Proc proc) {
    this._filer = new FilerImpl(context, fileManager, incrementalCompiler, this, proc);
    this._messager = new MessagerImpl(context, this);
    this._processorOptions = processorOptions != null ? processorOptions : Collections.<String, String>emptyMap();
    this._compiler = compiler;
  }

  @Override
  public Locale getLocale() {
    return Locale.getDefault(); // TODO
  }

  public void hardReset() {
    reset();
    setErrorRaised(false);

    ((FilerImpl) _filer).hardReset();
  }
}
