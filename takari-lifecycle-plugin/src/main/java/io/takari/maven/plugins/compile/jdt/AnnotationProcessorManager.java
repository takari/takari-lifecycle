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
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseAnnotationProcessorManager;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.apt.dispatch.ProcessorInfo;
import org.eclipse.jdt.internal.compiler.apt.dispatch.RoundDispatcher;
import org.eclipse.jdt.internal.compiler.apt.dispatch.RoundEnvImpl;
import org.eclipse.jdt.internal.compiler.apt.model.ElementImpl;
import org.eclipse.jdt.internal.compiler.apt.model.TypeElementImpl;
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

  private boolean suppressRegularRounds = false;
  private boolean suppressLastRound = false;

  private final Iterator<Processor> processors;

  private final CompilerJdt incrementalCompiler;

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

  private static class DiscoveredProcessors implements Iterator<Processor> {

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
  }

  public AnnotationProcessorManager(CompilerBuildContext context, ProcessingEnvImpl processingEnv, StandardJavaFileManager fileManager, String[] processors, CompilerJdt incrementalCompiler) {
    this.context = context;
    this._processingEnv = processingEnv;
    this.incrementalCompiler = incrementalCompiler;
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
      ProcessorInfo procecssorInfo = new ProcessorInfo(processor) {

        // the goal is to notify incrementalCompiler when annotation processing is taking place
        // as of jdt.apt 1.3.0 this method is called right before running an annotation processor
        // which is close enough to what we need
        @Override
        public boolean computeSupportedAnnotations(Set<TypeElement> annotations, Set<TypeElement> result) {
          boolean shouldCall = super.computeSupportedAnnotations(annotations, result);
          if (shouldCall) {
            incrementalCompiler.onAnnotationProcessing();
          }
          return shouldCall;
        }
      };
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
  public void incrementalIterationReset() {
    ((ProcessingEnvImpl) _processingEnv).incrementalIterationReset();
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
            File sourceFile = getSourceFile((ElementImpl) element);
            if (sourceFile != null) {
              processedSources.add(sourceFile);
            }
          }
        }

        return elements;
      } finally {
        recordingReferencedTypes = _recordingReferencedTypes;
      }
    }

    /**
     * Given {@code Element}, returns source file that defines the element. Returns {@code null} if the element is not defined in a source file.
     */
    private File getSourceFile(ElementImpl element) {
      TypeElementImpl topLevelType = getTopLevelType(element);
      if (topLevelType == null) {
        // TODO package-info.java annotation?
        return null;
      }
      Binding binding = topLevelType._binding;
      if (binding instanceof SourceTypeBinding) {
        return new File(new String(((SourceTypeBinding) binding).getFileName()));
      }
      return null;
    }

    /**
     * Returns enclosing top-level type of the element. Returns {@code null} if the element does not have enclosing top-level type.
     */
    private TypeElementImpl getTopLevelType(ElementImpl element) {
      for (; element != null; element = (ElementImpl) element.getEnclosingElement()) {
        if (element instanceof TypeElementImpl && ((TypeElementImpl) element).getNestingKind() == NestingKind.TOP_LEVEL) {
          return (TypeElementImpl) element;
        }
      }
      return null;
    }
  }

  // copied from BaseAnnotationProcessorManager in order to create custom RoundEnvImpl impl. boo.
  @Override
  public void processAnnotations(CompilationUnitDeclaration[] units, ReferenceBinding[] referenceBindings, boolean isLastRound) {
    if (!isLastRound && suppressRegularRounds) {
      return;
    }

    if (isLastRound && suppressLastRound) {
      return;
    }

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

  public void suppressRegularRounds(boolean suppress) {
    this.suppressRegularRounds = suppress;
  }

  public void suppressLastRound(boolean suppress) {
    this.suppressLastRound = suppress;
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
