package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseAnnotationProcessorManager;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.apt.dispatch.ProcessorInfo;
import org.eclipse.jdt.internal.compiler.apt.dispatch.RoundDispatcher;
import org.eclipse.jdt.internal.compiler.apt.dispatch.RoundEnvImpl;
import org.eclipse.jdt.internal.compiler.apt.model.ElementImpl;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;

import com.google.common.collect.ImmutableSet;

import io.takari.incrementalbuild.MessageSeverity;
import io.takari.maven.plugins.compile.CompilerBuildContext;

// TODO reconcile with BatchAnnotationProcessorManager
class AnnotationProcessorManager extends BaseAnnotationProcessorManager {

  private final CompilerBuildContext context;

  private final Set<File> processedSources = new LinkedHashSet<>();

  private boolean recordingReferencedTypes = true;
  private final Set<String> referencedTypes = new LinkedHashSet<>();

  private static interface ResettableProcessorIterator extends Iterator<Processor> {

    void reset();

  }

  private final ResettableProcessorIterator processors;

  private static class SpecifiedProcessors implements ResettableProcessorIterator {

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

    @Override
    public void reset() {
      idx = 0;
    }
  }

  private static class DiscoveredProcessors implements ResettableProcessorIterator {

    private final ServiceLoader<Processor> loader;
    private Iterator<Processor> iterator;

    public DiscoveredProcessors(ClassLoader procLoader) {
      this.loader = ServiceLoader.load(Processor.class, procLoader);
      this.iterator = loader.iterator();
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public Processor next() {
      return iterator.next();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
      this.iterator = loader.iterator();
    }

  }

  public AnnotationProcessorManager(CompilerBuildContext context, ProcessingEnvImpl processingEnv, StandardJavaFileManager fileManager, String[] processors) {
    this.context = context;
    this._processingEnv = processingEnv;
    ClassLoader procLoader = fileManager.getClassLoader(StandardLocation.ANNOTATION_PROCESSOR_PATH);
    this.processors = processors != null //
        ? new SpecifiedProcessors(procLoader, processors) //
        : new DiscoveredProcessors(procLoader);
    processingEnv.addReferencedTypeObserver(this::recordReferencedType);
  }

  private void recordReferencedType(String type) {
    if (recordingReferencedTypes) {
      referencedTypes.add(type);
    }
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
    String msg = String.format("Exception executing annotation processor %s: %s", p.getClass().getName(), e.getMessage());
    context.addPomMessage(msg, MessageSeverity.ERROR, e);
    throw new AbortCompilation(null, e);
  }

  /**
   * Resets this annotation processor manager between incremental compiler loop iterations.
   */
  public void hardReset() {
    // clear/reset parent state
    ((ProcessingEnvImpl) _processingEnv).hardReset();
    _processors.clear();
    _isFirstRound = true;
    _round = 0;
    // clear/reset this class state
    processors.reset();
  }

  private class _RoundEnvImpl extends RoundEnvImpl {

    public _RoundEnvImpl(CompilationUnitDeclaration[] units, ReferenceBinding[] binaryTypeBindings, boolean isLastRound, BaseProcessingEnvImpl env) {
      super(units, binaryTypeBindings, isLastRound, env);
    }

    // NB getElementsAnnotatedWith(Class) delegates to getElementsAnnotatedWith(TypeElement)

    @Override
    public Set<? extends Element> getElementsAnnotatedWith(TypeElement a) {
      return recordProcessedSources(() -> super.getElementsAnnotatedWith(a));
    }

    @Override
    public Set<? extends Element> getRootElements() {
      return recordProcessedSources(() -> super.getRootElements());
    }

    private Set<? extends Element> recordProcessedSources(Supplier<Set<? extends Element>> elementsSupplier) {
      final boolean _recordingReferencedTypes = recordingReferencedTypes;
      try {
        recordingReferencedTypes = false;
        Set<? extends Element> elements = elementsSupplier.get();
        if (_recordingReferencedTypes) {
          for (Element element : elements) {
            if (!(element instanceof ElementImpl)) {
              throw new IllegalArgumentException();
            }
            Binding binding = ((ElementImpl) element)._binding;
            if (binding instanceof SourceTypeBinding) {
              File file = new File(new String(((SourceTypeBinding) binding).getFileName()));
              processedSources.add(file);
            }
          }
        }

        return elements;
      } finally {
        recordingReferencedTypes = _recordingReferencedTypes;
      }
    }
  }

  // copied from BaseAnnotationProcessorManager in order to create custom RoundEnvImpl impl. boo.
  @Override
  public void processAnnotations(CompilationUnitDeclaration[] units, ReferenceBinding[] referenceBindings, boolean isLastRound) {
    RoundEnvImpl roundEnv = new _RoundEnvImpl(units, referenceBindings, isLastRound, _processingEnv);
    if (_isFirstRound) {
      _isFirstRound = false;
    }
    PrintWriter traceProcessorInfo = _printProcessorInfo ? _out : null;
    PrintWriter traceRounds = _printRounds ? _out : null;
    if (traceRounds != null) {
      traceRounds.println("Round " + ++_round + ':'); //$NON-NLS-1$
    }
    RoundDispatcher dispatcher = new RoundDispatcher(this, roundEnv, roundEnv.getRootAnnotations(), traceProcessorInfo, traceRounds);
    dispatcher.round();
  }

  public Set<File> getProcessedSources() {
    return ImmutableSet.copyOf(processedSources);
  }

  public ReferenceCollection getReferencedTypes() {
    ReferenceCollection references = new ReferenceCollection();
    references.addDependencies(referencedTypes);
    return references;
  }

  public Set<File> getWittenOutputs() {
    return ((FilerImpl) _processingEnv.getFiler()).getWrittenFiles();
  }
}
