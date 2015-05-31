package io.takari.maven.plugins.compile.jdt;

import java.util.Iterator;
import java.util.ServiceLoader;

import javax.annotation.processing.Processor;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseAnnotationProcessorManager;
import org.eclipse.jdt.internal.compiler.apt.dispatch.ProcessorInfo;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO reconcile with BatchAnnotationProcessorManager
class AnnotationProcessorManager extends BaseAnnotationProcessorManager {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private final Iterator<Processor> processors;

  private boolean finished;

  private static class SpecifiedProcessors implements Iterator<Processor> {

    private final ClassLoader loader;
    private final String[] processors;
    private int idx;

    public SpecifiedProcessors(ClassLoader loader, String[] processors) {
      this.loader = loader;
      this.processors = processors;
    }

    @Override
    public boolean hasNext() {
      return idx < processors.length;
    }

    @Override
    public Processor next() {
      try {
        return (Processor) loader.loadClass(processors[idx++]).newInstance();
      } catch (ReflectiveOperationException e) {
        // TODO: better error handling
        throw new AbortCompilation(null, e);
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  public AnnotationProcessorManager(ProcessingEnvImpl processingEnv, StandardJavaFileManager fileManager, String[] processors) {
    this._processingEnv = processingEnv;
    ClassLoader procLoader = fileManager.getClassLoader(StandardLocation.ANNOTATION_PROCESSOR_PATH);
    this.processors = processors != null //
        ? new SpecifiedProcessors(procLoader, processors) //
        : ServiceLoader.load(Processor.class, procLoader).iterator();
  }

  @Override
  public ProcessorInfo discoverNextProcessor() {
    if (processors.hasNext()) {
      Processor processor = processors.next();
      processor.init(_processingEnv);
      ProcessorInfo procecssorInfo = new ProcessorInfo(processor);
      _processors.add(procecssorInfo); // TODO this needs to happen in RoundDispatcher.round()
      return procecssorInfo;
    }
    return null;
  }

  @Override
  public void reportProcessorException(Processor p, Exception e) {
    // TODO Auto-generated method stub
    throw new AbortCompilation(null, e);
  }

  @Override
  public void processAnnotations(CompilationUnitDeclaration[] units, ReferenceBinding[] referenceBindings, boolean isLastRound) {
    if (finished) {
      // workaround eclipse jdt compiler bug 468893
      logger.warn("Suppressed duplicate processingOver==true annotation processor invocation, see Eclipse bug 468893");
      return;
    }
    finished = isLastRound;
    super.processAnnotations(units, referenceBindings, isLastRound);
  }
}
