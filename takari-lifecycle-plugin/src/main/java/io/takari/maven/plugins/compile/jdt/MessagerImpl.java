package io.takari.maven.plugins.compile.jdt;

import java.io.File;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

import org.eclipse.jdt.internal.compiler.apt.dispatch.AptProblem;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseMessagerImpl;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;

import io.takari.incrementalbuild.MessageSeverity;
import io.takari.incrementalbuild.Resource;
import io.takari.maven.plugins.compile.CompilerBuildContext;

class MessagerImpl extends BaseMessagerImpl implements Messager {

  private final CompilerBuildContext context;
  private final BaseProcessingEnvImpl _processingEnv;

  public MessagerImpl(CompilerBuildContext context, BaseProcessingEnvImpl _processingEnv) {
    this.context = context;
    this._processingEnv = _processingEnv;
  }

  @Override
  public void printMessage(Kind kind, CharSequence msg) {
    printMessage(kind, msg, null, null, null);
  }

  @Override
  public void printMessage(Kind kind, CharSequence msg, Element e) {
    printMessage(kind, msg, e, null, null);
  }

  @Override
  public void printMessage(Kind kind, CharSequence msg, Element e, AnnotationMirror a) {
    printMessage(kind, msg, e, null, null);
  }

  @Override
  public void printMessage(Kind kind, CharSequence msg, Element e, AnnotationMirror a, AnnotationValue v) {
    if (kind == Kind.ERROR) {
      _processingEnv.setErrorRaised(true);
    }
    AptProblem problem = createProblem(kind, msg, e, a, v);
    if (problem != null && problem.getOriginatingFileName() != null) {
      Resource<File> input = context.getProcessedSource(new File(new String(problem.getOriginatingFileName())));
      input.addMessage(problem.getSourceLineNumber(), problem.getSourceColumnNumber(), problem.getMessage(), getSeverity(kind), null);
    } else {
      context.addPomMessage(msg.toString(), getSeverity(kind), null);
    }
  }

  private MessageSeverity getSeverity(Kind kind) {
    switch (kind) {
      case ERROR:
        return MessageSeverity.ERROR;
      case WARNING:
      case MANDATORY_WARNING:
        return MessageSeverity.WARNING;
      default:
        return MessageSeverity.INFO;
    }
  }

}
