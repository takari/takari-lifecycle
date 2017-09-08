/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import static io.takari.maven.plugins.compile.CompilerBuildContext.isJavaSource;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;
import javax.tools.StandardLocation;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.apt.util.EclipseFileManager;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.IrritantSet;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;
import org.eclipse.jdt.internal.core.builder.ProblemFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;

import io.takari.incrementalbuild.MessageSeverity;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;
import io.takari.maven.plugins.compile.AbstractCompileMojo.AccessRulesViolation;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Debug;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;
import io.takari.maven.plugins.compile.AbstractCompiler;
import io.takari.maven.plugins.compile.CompilerBuildContext;
import io.takari.maven.plugins.compile.ProjectClasspathDigester;
import io.takari.maven.plugins.compile.jdt.classpath.Classpath;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathDirectory;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.DependencyClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.JavaInstallation;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.SourcepathDirectory;

/**
 * @TODO test classpath order changes triggers rebuild of affected sources (same type name, different classes)
 * @TODO figure out why JDT needs to worry about duplicate types (maybe related to classpath order above)
 * @TODO test affected sources are recompiled after source gets compile error
 * @TODO test nested types because addDependentsOf has some special handling
 */
@Named(CompilerJdt.ID)
public class CompilerJdt extends AbstractCompiler implements ICompilerRequestor {
  public static final String ID = "jdt";

  /*
   * Notes on build context usage by this compiler implementation.
   * 
   * Classpath, sourcepath and processorpath digests are persisted as context-level attributes (internally, the context associates them with project pom.xml). Classpath (should really be called
   * "compile path") "structural" digest algorithm ignores changes to class method bodies and avoids unnecessary recompilations (see ClassfileDigester). Sourcepath and processorpath digest algorithm
   * is common for all compiler implementations (see ProjectClasspathDigester).
   * 
   * Java source ReferenceCollection is persisted as input file attribute.
   * 
   * Output .class file "structural" digest is persisted as output file attribute.
   * 
   * Tracking of inputs, outputs and their associations:
   * 
   * - Build context tracks association between java sources and their corresponding class files.
   * 
   * - Build context does NOT track originating elements of source/resources generated during annotation processing. All sources processed by apt and all written outputs are tracked in
   * AnnotationProcessingState. Note that build context still tracks association between apt-generated sources and their corresponding class files. Annotation processing is performed "atomically",
   * that is, if any source requires (re)processing, all apt outputs generated during previous builds are declared stale (see below) and all sources are (re)processed.
   * 
   * - Outputs associated with modified sources are marked as "stale" before compilation but not physically deleted. At the end of compilation stales outputs that were not overwritten are deleted.
   * This is meant to work with IncrementalFileOutputStream, which does not modify filesystem when incremental compilation regenerates exactly the same content.
   * 
   * - Outputs associated with removed inputs are deleted before compilation.
   */

  /**
   * Output .class file structure hash
   */
  private static final String ATTR_CLASS_DIGEST = "jdt.class.digest";

  /**
   * Classpath digest, map of accessible types to their .class structure hashes.
   */
  private static final String ATTR_CLASSPATH_DIGEST = "jdt.classpath.digest";

  /**
   * Annotation processing state
   * 
   * @see AnnotationProcessingState
   */
  private static final String ATTR_APTSTATE = "jdt.aptstate";

  /**
   * Java source {@link ReferenceCollection}
   */
  private static final String ATTR_REFERENCES = "jdt.references";

  private List<File> dependencies;

  private List<File> processorpath;

  private List<ClasspathEntry> sourcepath = Collections.emptyList();

  private List<ClasspathEntry> dependencypath;

  private final Map<File, ResourceMetadata<File>> sources = new LinkedHashMap<>();

  /**
   * Set of ICompilationUnit to be compiled.
   */
  private final Map<File, ICompilationUnit> compileQueue = new LinkedHashMap<>();

  private final ClassfileDigester digester = new ClassfileDigester();

  private final ClasspathEntryCache classpathCache;

  private final ClasspathDigester classpathDigester;

  private final ProjectClasspathDigester processorpathDigester;

  private abstract class CompilationStrategy {

    /**
     * To-be-deleted output files created during prior builds. These files are hidden from compile path.
     * 
     * @see OutputDirectoryClasspathEntry#OutputDirectoryClasspathEntry(File, Collection).
     */
    protected final Set<File> staleOutputs = new HashSet<>();

    protected AnnotationProcessingState aptstate;

    public abstract boolean setSources(List<ResourceMetadata<File>> sources) throws IOException;

    public abstract void enqueueAffectedSources(HashMap<String, byte[]> digest, Map<String, byte[]> oldDigest) throws IOException;

    public abstract void enqueueAllSources() throws IOException;

    public abstract void addDependentsOf(String typeOrPackage);

    protected abstract void addDependentsOf(File resource);

    public abstract int compile(Classpath namingEnvironment, Compiler compiler) throws IOException;

    protected CompilationStrategy() {
      if (!isProcNone()) {
        aptstate = context.getAttribute(ATTR_APTSTATE, true, AnnotationProcessingState.class);
      }
    }

    public Classpath createClasspath() throws IOException {
      return CompilerJdt.this.createClasspath(staleOutputs);
    }

    public abstract void addGeneratedSource(Output<File> generatedSource);

    protected boolean deleteOrphanedOutputs() throws IOException {
      boolean changed = false;
      for (ResourceMetadata<File> source : context.getRemovedSources()) {
        changed = deleteAssociatedOutputs(source) || changed;
      }

      return changed;
    }

    protected boolean deleteAssociatedOutputs(ResourceMetadata<File> resource) throws IOException {
      return deleteOutputs(context.getAssociatedOutputs(resource));
    }

    protected boolean deleteAssociatedOutputs(File resource) throws IOException {
      return deleteOutputs(context.getAssociatedOutputs(resource));
    }

    protected boolean deleteOutputs(Collection<ResourceMetadata<File>> outputs) throws IOException {
      for (ResourceMetadata<File> output : outputs) {
        deleteOutput(output.getResource());
      }
      return !outputs.isEmpty();
    }

    protected void deleteOutput(File outputFile) throws IOException {
      if (isJavaSource(outputFile)) {
        deleteAssociatedOutputs(outputFile);
      }
      context.deleteOutput(outputFile);
      addDependentsOf(outputFile);
    }

    protected boolean deleteStaleOutputs() throws IOException {
      boolean changed = false;
      for (File staleOutput : staleOutputs) {
        // TODO is it possible for an output to be processed in one compilation iteration but become obsolete during the next?
        if (!context.isProcessedOutput(staleOutput)) {
          deleteOutput(staleOutput);
          changed = true;
        }
      }
      staleOutputs.clear();
      return changed;
    }

    /**
     * Marks sources as "processed" in the build context. Masks old associated outputs from naming environments by adding them to {@link #staleOutputs}.
     */
    protected void processSources() {
      for (File sourceFile : compileQueue.keySet()) {
        ResourceMetadata<File> source = sources.get(sourceFile);
        for (ResourceMetadata<File> output : context.getAssociatedOutputs(source)) {
          staleOutputs.add(output.getResource());
        }
        sources.put(source.getResource(), context.processInput(source));
      }
    }

    public abstract void onAnnotationProcessing();

    public void skipCompile() {
      if (aptstate != null) {
        context.setAttribute(ATTR_APTSTATE, aptstate);
      }
    }
  }

  private class IncrementalCompilationStrategy extends CompilationStrategy {

    /**
     * Set of File that have already been added to the compile queue during this incremental compile loop iteration.
     */
    private final Set<File> processedQueue = new HashSet<>();

    /**
     * Set of File that have already been added to the compile queue.
     */
    private final Multiset<File> processedSources = HashMultiset.create();


    private final Set<String> rootNames = new LinkedHashSet<>();

    private final Set<String> qualifiedNames = new LinkedHashSet<>();

    private final Set<String> simpleNames = new LinkedHashSet<>();

    @Override
    public int compile(Classpath namingEnvironment, Compiler compiler) throws IOException {
      AnnotationProcessorManager aptmanager = (AnnotationProcessorManager) compiler.annotationProcessorManager;

      if (aptmanager != null) {
        // suppress APT lastRound during incremental compile loop
        aptmanager.suppressLastRound(true);
      }

      // incremental compilation loop
      // keep calling the compiler while there are sources in the queue
      incrementalCompilationLoop(namingEnvironment, compiler, aptmanager);

      // run apt last round iff doing annotation processing and ran regular apt rounds
      if (aptmanager != null && aptstate == null) {
        // tell apt manager we are running APT lastRound
        aptmanager.suppressRegularRounds(true);
        aptmanager.suppressLastRound(false);

        // even if a class was processed it would need recompiled once types are generated.
        processedQueue.clear();

        // trick the compiler to run APT without any source or binary types
        compiler.referenceBindings = new ReferenceBinding[0];
        compiler.compile(new ICompilationUnit[0]);
        namingEnvironment.reset();
        aptmanager.incrementalIterationReset();
        deleteStaleOutputs();

        enqueueAffectedSources();
        // all apt rounds are now suppressed.
        aptmanager.suppressLastRound(true);
        // compile class that rely on apt completing
        incrementalCompilationLoop(namingEnvironment, compiler, aptmanager);
      }

      persistAnnotationProcessingState(compiler, aptstate);

      return processedSources.size();
    }

    /**
     * This loop handles the incremental compilation of classes in the compileQueue. Regular apt rounds may occur in this loop, but the apt final round will not.
     * 
     * This loop will be called once after the apt final round is processed to compile all effected types. All prior error/warn/info messages, referenced types, generated outputs and other per-input
     * information tracked by in build context are ignored when a type is recompiled.
     */
    private void incrementalCompilationLoop(Classpath namingEnvironment, Compiler compiler, AnnotationProcessorManager aptmanager) throws IOException {
      while (!compileQueue.isEmpty() || !staleOutputs.isEmpty()) {
        processedQueue.clear();
        processedQueue.addAll(compileQueue.keySet());

        // All prior error/warn/info messages, referenced types, generated outputs and other per-input information tracked by in build context are wiped away within.
        processSources();

        // invoke the compiler
        ICompilationUnit[] compilationUnits = compileQueue.values().toArray(new ICompilationUnit[compileQueue.size()]);
        compileQueue.clear();
        compiler.compile(compilationUnits);
        namingEnvironment.reset();

        if (aptmanager != null) {
          aptmanager.incrementalIterationReset();
        }

        deleteStaleOutputs(); // delete stale outputs and enqueue affected sources

        enqueueAffectedSources();
      }
    }

    @Override
    protected void addDependentsOf(File resource) {
      addDependentsOf(getJavaType(resource));
    }

    @Override
    public boolean setSources(List<ResourceMetadata<File>> sources) throws IOException {
      for (ResourceMetadata<File> source : sources) {
        CompilerJdt.this.sources.put(source.getResource(), source);
        if (source.getStatus() != ResourceStatus.UNMODIFIED) {
          enqueue(source);
        }
      }

      boolean compilationRequired = false;

      // delete orphaned outputs and rebuild all sources that reference them
      compilationRequired = deleteOrphanedOutputs() || compilationRequired;

      enqueueAffectedSources();

      return compilationRequired || !compileQueue.isEmpty();
    }

    private void enqueueAllAnnotatedSources() {
      Set<File> annotatedSources = aptstate.processedSources;
      Set<File> writtenOutputs = aptstate.writtenOutputs;
      aptstate = null; // this needs to be before enqueue(File) to avoid recursion
      for (File annotatedSource : annotatedSources) {
        if (annotatedSource.isFile()) {
          enqueue(annotatedSource);
        }
      }
      for (File writtenOutput : writtenOutputs) {
        staleOutputs.add(writtenOutput);
        context.getAssociatedOutputs(writtenOutput).forEach(output -> staleOutputs.add(output.getResource()));
      }
    }

    private void enqueueAffectedSources() {
      if (aptstate != null && aptstate.referencedTypes.includes(qualifiedNames, simpleNames, rootNames)) {
        enqueueAllAnnotatedSources();
      }
      for (ResourceMetadata<File> input : sources.values()) {
        final File resource = input.getResource();
        if (!processedQueue.contains(resource) && resource.canRead()) {
          ReferenceCollection references = context.getAttribute(resource, ATTR_REFERENCES, ReferenceCollection.class);
          if (references != null && references.includes(qualifiedNames, simpleNames, rootNames)) {
            enqueue(input);
          }
        }
      }

      qualifiedNames.clear();
      simpleNames.clear();
      rootNames.clear();
    }

    private String getJavaType(File outputFile) {
      String outputDirectory = getOutputDirectory().getAbsolutePath();
      String path = outputFile.getAbsolutePath();
      if (!path.startsWith(outputDirectory) || !path.endsWith(".class")) {
        return null;
      }
      path = path.substring(outputDirectory.length(), path.length() - ".class".length());
      if (path.startsWith(File.separator)) {
        path = path.substring(1);
      }
      return path.replace(File.separatorChar, '.');
    }

    @Override
    public void enqueueAllSources() throws IOException {
      for (ResourceMetadata<File> input : sources.values()) {
        final File resource = input.getResource();
        if (!processedQueue.contains(resource) && resource.canRead()) {
          enqueue(input);
        }
      }

      qualifiedNames.clear();
      simpleNames.clear();
      rootNames.clear();
    }

    @Override
    public void addDependentsOf(String typeOrPackage) {
      if (typeOrPackage != null) {
        // adopted from org.eclipse.jdt.internal.core.builder.IncrementalImageBuilder.addDependentsOf
        // TODO deal with package-info
        int idx = typeOrPackage.indexOf('.');
        if (idx > 0) {
          rootNames.add(typeOrPackage.substring(0, idx));
          idx = typeOrPackage.lastIndexOf('.');
          qualifiedNames.add(typeOrPackage.substring(0, idx));
          simpleNames.add(typeOrPackage.substring(idx + 1));
        } else {
          rootNames.add(typeOrPackage);
          simpleNames.add(typeOrPackage);
        }
      }
    }

    private void enqueue(ResourceMetadata<File> input) {
      enqueue(input.getResource());
    }

    private void enqueue(File sourceFile) {
      if (processedSources.count(sourceFile) > 15) {
        // this is meant to prevent endless recompiles and exact number of recompiles is not important
        // note that processedSources can be incremented multiple times for the same source during compilation bootstrap
        // - the source has changed
        // - any type referenced by the source has changed
        // - any annotated source has changed
        // - any typed referenced during annotation processing has changed
        throw new IllegalStateException("Too many recompiles " + sourceFile);
      }
      if (aptstate != null && aptstate.processedSources.contains(sourceFile)) {
        enqueueAllAnnotatedSources();
      } else {
        processedSources.add(sourceFile);
        compileQueue.put(sourceFile, newSourceFile(sourceFile));
      }
    }


    @Override
    public void enqueueAffectedSources(HashMap<String, byte[]> digest, Map<String, byte[]> oldDigest) throws IOException {
      if (oldDigest != null) {
        Set<String> changedPackages = new HashSet<String>();

        for (Map.Entry<String, byte[]> entry : digest.entrySet()) {
          String type = entry.getKey();
          byte[] hash = entry.getValue();
          if (!Arrays.equals(hash, oldDigest.get(type))) {
            addDependentsOf(type);
          }
          changedPackages.add(getPackage(type));
        }

        for (String oldType : oldDigest.keySet()) {
          if (!digest.containsKey(oldType)) {
            addDependentsOf(oldType);
          }
          changedPackages.remove(getPackage(oldType));
        }

        for (String changedPackage : changedPackages) {
          addDependentsOf(changedPackage);
        }

        enqueueAffectedSources();
      }
    }

    private String getPackage(String type) {
      int idx = type.lastIndexOf('.');
      return idx > 0 ? type.substring(0, idx) : null;
    }

    @Override
    public void addGeneratedSource(Output<File> generatedSource) {
      sources.put(generatedSource.getResource(), generatedSource);
      processedQueue.add(generatedSource.getResource());
    }

    @Override
    public void onAnnotationProcessing() {
      if (aptstate != null) {
        enqueueAllAnnotatedSources();
      }
    }

    @Override
    protected boolean deleteAssociatedOutputs(ResourceMetadata<File> resource) throws IOException {
      boolean changed = false;
      if (aptstate != null && aptstate.processedSources.contains(resource.getResource())) {
        enqueueAllAnnotatedSources();
        changed = true;
      }
      return super.deleteAssociatedOutputs(resource) || changed;
    }
  }

  private class FullCompilationStrategy extends CompilationStrategy {

    @Override
    public boolean setSources(List<ResourceMetadata<File>> sources) throws IOException {
      for (ResourceMetadata<File> source : sources) {
        File sourceFile = source.getResource();
        CompilerJdt.this.sources.put(sourceFile, source);
        compileQueue.put(sourceFile, newSourceFile(sourceFile));
      }

      deleteOrphanedOutputs();

      return true;
    }

    @Override
    public void enqueueAffectedSources(HashMap<String, byte[]> digest, Map<String, byte[]> oldDigest) throws IOException {
      // full strategy compiles all sources in one pass
    }

    @Override
    public void enqueueAllSources() throws IOException {
      // full strategy compiles all sources in one pass
    }

    @Override
    public void addDependentsOf(String string) {
      // full strategy compiles all sources in one pass
    }

    @Override
    protected void addDependentsOf(File resource) {
      // full strategy compiles all sources in one pass
    }

    @Override
    public int compile(Classpath namingEnvironment, Compiler compiler) throws IOException {
      processSources();

      if (aptstate != null) {
        staleOutputs.addAll(aptstate.writtenOutputs);
        aptstate = null;
      }

      if (!compileQueue.isEmpty()) {
        ICompilationUnit[] compilationUnits = compileQueue.values().toArray(new ICompilationUnit[compileQueue.size()]);
        compiler.compile(compilationUnits);
      }

      persistAnnotationProcessingState(compiler, null);

      deleteStaleOutputs();

      return compileQueue.size();
    }

    @Override
    public void addGeneratedSource(Output<File> generatedSource) {
      // full strategy compiles all sources in one pass
    }

    @Override
    public void onAnnotationProcessing() {
      // full strategy compiles all sources in one pass
    }
  }

  private CompilationStrategy strategy;

  @Inject
  public CompilerJdt(CompilerBuildContext context, ClasspathEntryCache classpathCache, ClasspathDigester classpathDigester, ProjectClasspathDigester processorpathDigester) {
    super(context);
    this.classpathCache = classpathCache;
    this.classpathDigester = classpathDigester;
    this.processorpathDigester = processorpathDigester;

    this.strategy = context.isEscalated() ? new FullCompilationStrategy() : new IncrementalCompilationStrategy();
  }

  @Override
  public int compile() throws IOException {
    Map<String, String> args = new HashMap<String, String>();
    // XXX figure out how to reuse source/target check from jdt
    // org.eclipse.jdt.internal.compiler.batch.Main.validateOptions(boolean)
    args.put(CompilerOptions.OPTION_TargetPlatform, getTarget()); // support 5/6/7 aliases
    args.put(CompilerOptions.OPTION_Compliance, getTarget()); // support 5/6/7 aliases
    args.put(CompilerOptions.OPTION_Source, getSource()); // support 5/6/7 aliases
    args.put(CompilerOptions.OPTION_ReportForbiddenReference, CompilerOptions.ERROR);
    Set<Debug> debug = getDebug();
    if (debug == null || debug.contains(Debug.all)) {
      args.put(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.GENERATE);
      args.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE);
      args.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.GENERATE);
    } else if (debug.contains(Debug.none)) {
      args.put(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.DO_NOT_GENERATE);
      args.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.DO_NOT_GENERATE);
      args.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.DO_NOT_GENERATE);
    } else {
      args.put(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.DO_NOT_GENERATE);
      args.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.DO_NOT_GENERATE);
      args.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.DO_NOT_GENERATE);
      for (Debug keyword : debug) {
        switch (keyword) {
          case lines:
            args.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE);
            break;
          case source:
            args.put(CompilerOptions.OPTION_SourceFileAttribute, CompilerOptions.GENERATE);
            break;
          case vars:
            args.put(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.GENERATE);
            break;
          default:
            throw new IllegalArgumentException();
        }
      }
    }

    class _CompilerOptions extends CompilerOptions {
      public void setShowWarnings(boolean showWarnings) {
        if (showWarnings) {
          warningThreshold = IrritantSet.COMPILER_DEFAULT_WARNINGS;
        } else {
          warningThreshold = new IrritantSet(0);
        }
      }
    }
    _CompilerOptions compilerOptions = new _CompilerOptions();

    compilerOptions.set(args);
    compilerOptions.performMethodsFullRecovery = false;
    compilerOptions.performStatementsRecovery = false;
    compilerOptions.verbose = isVerbose();
    compilerOptions.produceMethodParameters = isParameters();
    compilerOptions.suppressWarnings = true;
    compilerOptions.setShowWarnings(isShowWarnings());
    compilerOptions.docCommentSupport = true;

    if (!sourcepath.isEmpty() && strategy instanceof IncrementalCompilationStrategy) {
      strategy.enqueueAllSources();
      strategy = new FullCompilationStrategy();
    }

    Classpath namingEnvironment = strategy.createClasspath();
    IErrorHandlingPolicy errorHandlingPolicy = DefaultErrorHandlingPolicies.exitAfterAllProblems();
    IProblemFactory problemFactory = ProblemFactory.getProblemFactory(Locale.getDefault());
    Compiler compiler = new Compiler(namingEnvironment, errorHandlingPolicy, compilerOptions, this, problemFactory) {
      @Override
      protected synchronized void addCompilationUnit(ICompilationUnit sourceUnit, CompilationUnitDeclaration parsedUnit) {
        if (sourceUnit instanceof SourcepathDirectory.ClasspathCompilationUnit) {
          // this compilation unit represents dependency .java file
          // it is used to resolve type references and must not be processed otherwise
          return;
        }
        // growth of the internal unitsToProcess array is handled via multiplication of it's current size,
        // so if size is 0, we should just go ahead and handle it here.
        if (this.unitsToProcess.length == 0) {
          // start out with a size other than 0, so that it can be doubled safely by the super method.
          // starting with 4 to prevent the first couple of doublings and corresponding copying.
          this.unitsToProcess = new CompilationUnitDeclaration[4];
          this.unitsToProcess[0] = parsedUnit;
          // this tracks the units added, it must be incremented ever time a new type is added.
          this.totalUnits = 1;
        } else {
          super.addCompilationUnit(sourceUnit, parsedUnit);
        }
      }


    };
    compiler.options.produceReferenceInfo = true;

    EclipseFileManager fileManager = null;
    try {
      if (!isProcNone()) {
        fileManager = new EclipseFileManager(null, getSourceEncoding());
        fileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, processorpath);
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(getOutputDirectory()));
        fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, Collections.singleton(getGeneratedSourcesDirectory()));

        ProcessingEnvImpl processingEnv = new ProcessingEnvImpl(context, fileManager, getAnnotationProcessorOptions(), compiler, this);

        compiler.annotationProcessorManager = new AnnotationProcessorManager(context, processingEnv, fileManager, getAnnotationProcessors(), this);
        compiler.options.storeAnnotations = true;
      }

      return strategy.compile(namingEnvironment, compiler);
    } finally {
      if (fileManager != null) {
        fileManager.flush();
        fileManager.close();
      }
    }
  }

  private void persistAnnotationProcessingState(Compiler compiler, AnnotationProcessingState carryOverState) {
    if (compiler.annotationProcessorManager == null) {
      return; // not processing annotations
    }
    final AnnotationProcessingState aptstate;
    if (carryOverState != null) {
      // incremental build did not need to process annotations, carry over the state and outputs to the next build
      aptstate = carryOverState;
      aptstate.writtenOutputs.forEach(context::markUptodateOutput);
    } else {
      AnnotationProcessorManager aptmanager = (AnnotationProcessorManager) compiler.annotationProcessorManager;
      aptstate = new AnnotationProcessingState(aptmanager.getProcessedSources(), aptmanager.getReferencedTypes(), aptmanager.getWittenOutputs());
    }
    context.setAttribute(ATTR_APTSTATE, aptstate);
  }

  @Override
  public boolean setSources(List<ResourceMetadata<File>> sources) throws IOException {
    return strategy.setSources(sources);
  }

  private CompilationUnit newSourceFile(File source) {
    final String fileName = source.getAbsolutePath();
    final String encoding = getSourceEncoding() != null ? getSourceEncoding().name() : null;
    return new CompilationUnit(null, fileName, encoding, getOutputDirectory().getAbsolutePath(), false);
  }

  /**
   * @param staleOutputs is a <strong>live</strong> collection of output files to ignore.
   */
  private Classpath createClasspath(Collection<File> staleOutputs) throws IOException {
    final List<ClasspathEntry> entries = new ArrayList<ClasspathEntry>();
    final List<MutableClasspathEntry> mutableentries = new ArrayList<MutableClasspathEntry>();

    // XXX detect change!
    for (File file : JavaInstallation.getDefault().getClasspath()) {
      ClasspathEntry entry = classpathCache.get(file);
      if (entry != null) {
        entries.add(entry);
      }
    }

    if (!isProcOnly()) {
      OutputDirectoryClasspathEntry output = new OutputDirectoryClasspathEntry(getOutputDirectory(), staleOutputs);
      entries.add(output);
      mutableentries.add(output);
    }

    entries.addAll(sourcepath);
    entries.addAll(dependencypath);

    return new Classpath(entries, mutableentries);
  }

  @Override
  public boolean setClasspath(List<File> dependencies, File mainClasses, Set<File> directDependencies) throws IOException {
    this.dependencies = dependencies;

    final List<ClasspathEntry> dependencypath = new ArrayList<ClasspathEntry>();
    final List<File> files = new ArrayList<File>();

    if (isProcOnly()) {
      DependencyClasspathEntry entry = ClasspathDirectory.create(getOutputDirectory());
      if (entry != null) {
        dependencypath.add(AccessRestrictionClasspathEntry.allowAll(entry));
        files.add(getOutputDirectory());
      }
    }

    if (mainClasses != null) {
      DependencyClasspathEntry entry = classpathCache.get(mainClasses);
      if (entry != null) {
        dependencypath.add(AccessRestrictionClasspathEntry.allowAll(entry));
        files.add(mainClasses);
      }
    }

    for (File dependency : dependencies) {
      DependencyClasspathEntry entry = classpathCache.get(dependency);
      if (entry != null) {
        if (getTransitiveDependencyReference() == AccessRulesViolation.error && !directDependencies.contains(dependency)) {
          dependencypath.add(AccessRestrictionClasspathEntry.forbidAll(entry));
        } else if (getPrivatePackageReference() == AccessRulesViolation.ignore) {
          dependencypath.add(AccessRestrictionClasspathEntry.allowAll(entry));
        } else {
          dependencypath.add(entry);
        }
        files.add(dependency);
      }
    }

    if (log.isDebugEnabled()) {
      StringBuilder msg = new StringBuilder();
      for (ClasspathEntry element : dependencypath) {
        msg.append("\n   ").append(element.getEntryDescription());
      }
      log.debug("Compile classpath: {} entries{}", dependencies.size(), msg.toString());
    }

    this.dependencypath = ImmutableList.copyOf(dependencypath);

    Stopwatch stopwatch = Stopwatch.createStarted();
    long typecount = 0, packagecount = 0;

    HashMap<String, byte[]> digest = classpathDigester.digestDependencies(files);

    @SuppressWarnings("unchecked")
    Map<String, byte[]> oldDigest = (Map<String, byte[]>) context.setAttribute(ATTR_CLASSPATH_DIGEST, digest);

    log.debug("Digested {} types and {} packages in {} ms", typecount, packagecount, stopwatch.elapsed(TimeUnit.MILLISECONDS));

    strategy.enqueueAffectedSources(digest, oldDigest);

    return !compileQueue.isEmpty();
  }

  @Override
  public boolean setSourcepath(List<File> dependencies, Set<File> sourceRoots) throws IOException {
    List<ClasspathEntry> sourcepath = new ArrayList<>();
    for (File dependency : dependencies) {
      if (dependency.isDirectory()) {
        final DependencyClasspathEntry entry;
        if (sourceRoots.contains(dependency)) {
          // own source roots can be mutable, don't cache
          entry = SourcepathDirectory.create(dependency, getSourceEncoding());
        } else {
          entry = classpathCache.getSourcepathEntry(dependency, getSourceEncoding());
        }
        sourcepath.add(entry);
      } else if (dependency.isFile()) {
        throw new IllegalArgumentException();
      }
    }

    this.sourcepath = ImmutableList.copyOf(sourcepath);

    return processorpathDigester.digestSourcepath(dependencies);
  }

  @Override
  public boolean setProcessorpath(List<File> processorpath) throws IOException {
    if (processorpath == null) {
      this.processorpath = dependencies;
    } else {
      this.processorpath = ImmutableList.copyOf(processorpath);
    }
    if (!isProcNone() && processorpathDigester.digestProcessorpath(this.processorpath)) {
      log.debug("Annotation processor path changed, recompiling all sources");
      strategy.enqueueAllSources();
    }
    return !compileQueue.isEmpty();
  }

  @Override
  public void acceptResult(CompilationResult result) {
    if (result == null) {
      return; // ah?
    }

    final String sourceName = new String(result.getFileName());
    final File sourceFile = new File(sourceName);

    Resource<File> input = context.getProcessedSource(sourceFile);

    // track type references
    if (result.rootReferences != null && result.qualifiedReferences != null && result.simpleNameReferences != null) {
      context.setAttribute(input.getResource(), ATTR_REFERENCES, new ReferenceCollection(result.rootReferences, result.qualifiedReferences, result.simpleNameReferences));
    }

    if (result.hasProblems()) {
      for (CategorizedProblem problem : result.getProblems()) {
        if (problem.isError() || isShowWarnings()) {
          MessageSeverity severity = problem.isError() ? MessageSeverity.ERROR : MessageSeverity.WARNING;
          input.addMessage(problem.getSourceLineNumber(), ((DefaultProblem) problem).column, problem.getMessage(), severity, null /* cause */);
        }
      }
    }

    try {
      if (!result.hasErrors() && !isProcOnly()) {
        for (ClassFile classFile : result.getClassFiles()) {
          char[] filename = classFile.fileName();
          int length = filename.length;
          char[] relativeName = new char[length + 6];
          System.arraycopy(filename, 0, relativeName, 0, length);
          System.arraycopy(SuffixConstants.SUFFIX_class, 0, relativeName, length, 6);
          CharOperation.replace(relativeName, '/', File.separatorChar);
          String relativeStringName = new String(relativeName);
          writeClassFile(input, relativeStringName, classFile);
        }
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    // XXX double check affected sources are recompiled when this source has errors
  }

  private boolean isProcOnly() {
    return getProc() == Proc.only;
  }

  private boolean isProcNone() {
    return getProc() == Proc.none;
  }

  private void writeClassFile(Resource<File> input, String relativeStringName, ClassFile classFile) throws IOException {
    final byte[] bytes = classFile.getBytes();
    final File outputFile = new File(getOutputDirectory(), relativeStringName);

    // context.associatedOutput resets output attributes set during earlier iterations of this incremental compile
    // so deal with classfile digest before context.associatedOutput
    final byte[] oldHash = context.getAttribute(outputFile, ATTR_CLASS_DIGEST, byte[].class);
    final byte[] hash = digestClassFile(outputFile, bytes);
    boolean significantChange = oldHash == null || hash == null || !Arrays.equals(hash, oldHash);

    final Output<File> output = context.associatedOutput(input, outputFile);

    if (hash != null) {
      // TODO evaluate if this is useful
      // trade-off is between storing digest on disk between builds
      // and recomputing the hash each time class files are written
      context.setAttribute(outputFile, ATTR_CLASS_DIGEST, hash);
    }

    if (significantChange) {
      // find all sources that reference this type and put them into work queue
      strategy.addDependentsOf(CharOperation.toString(classFile.getCompoundName()));
    }

    try (final BufferedOutputStream os = new BufferedOutputStream(output.newOutputStream())) {
      os.write(bytes);
      os.flush();
    }
  }

  private byte[] digestClassFile(File outputFile, byte[] definition) {
    try {
      ClassFileReader reader = new ClassFileReader(definition, outputFile.getAbsolutePath().toCharArray());
      return digester.digest(reader);
    } catch (ClassFormatException e) {
      // ignore this class
    }
    return null;
  }

  public void addGeneratedSource(Output<File> generatedSource) {
    strategy.addGeneratedSource(generatedSource);
  }

  public void onAnnotationProcessing() {
    strategy.onAnnotationProcessing();
  }

  @Override
  public void skipCompile() {
    strategy.skipCompile();
    super.skipCompile();
  }
}
