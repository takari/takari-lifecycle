/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

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

import org.apache.maven.plugin.MojoExecutionException;
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
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.IrritantSet;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;
import org.eclipse.jdt.internal.core.builder.ProblemFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
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
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.DependencyClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.JavaInstallation;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;

/**
 * @TODO test classpath order changes triggers rebuild of affected sources (same type name, different classes)
 * @TODO figure out why JDT needs to worry about duplicate types (maybe related to classpath order above)
 * @TODO test affected sources are recompiled after source gets compile error
 * @TODO test nested types because addDependentsOf has some special handling
 */
@Named(CompilerJdt.ID)
public class CompilerJdt extends AbstractCompiler implements ICompilerRequestor {
  public static final String ID = "jdt";

  /**
   * Output .class file structure hash
   */
  private static final String ATTR_CLASS_DIGEST = "jdt.class.digest";

  /**
   * Classpath digest, map of accessible types to their .class structure hashes.
   */
  private static final String ATTR_CLASSPATH_DIGEST = "jdt.classpath.digest";

  /**
   * Java source {@link ReferenceCollection}
   */
  private static final String ATTR_REFERENCES = "jdt.references";

  private List<File> dependencies;

  private List<File> processorpath;

  private Classpath dependencypath;

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
    protected final Multimap<File, File> sourceOutputs = HashMultimap.create();

    public abstract boolean setSources(List<ResourceMetadata<File>> sources) throws IOException;

    public abstract void enqueueAffectedSources(HashMap<String, byte[]> digest, Map<String, byte[]> oldDigest) throws IOException;

    public abstract void enqueueAllSources() throws IOException;

    public abstract void addDependentsOf(String typeOrPackage);

    protected abstract void addDependentsOf(File resource);

    public abstract int compile(Classpath namingEnvironment, Compiler compiler) throws IOException;

    public Classpath createClasspath() throws IOException {
      return CompilerJdt.this.createClasspath(sourceOutputs.values());
    }

    public abstract void addGeneratedSource(Output<File> generatedSource);

    protected boolean deleteOrphanedOutputs() throws IOException {
      boolean changed = false;
      for (ResourceMetadata<File> source : context.getRemovedSources()) {
        for (ResourceMetadata<File> output : context.getAssociatedOutputs(source)) {
          File outputFile = output.getResource();
          context.deleteOutput(outputFile);
          addDependentsOf(outputFile);
          changed = true;
        }
      }

      return changed;
    }

    protected boolean deleteStaleOutputs() throws IOException {
      boolean changed = false;
      for (File sourceFile : sourceOutputs.keySet()) {
        for (File associatedOutput : sourceOutputs.get(sourceFile)) {
          if (!context.isProcessedOutput(associatedOutput)) {
            context.deleteOutput(associatedOutput);
            addDependentsOf(associatedOutput);
            changed = true;
          }
        }
      }
      return changed;
    }

    /**
     * Marks sources as "processed" in the build context. Masks old associated outputs from naming environments by adding them to {@link #sourceOutputs} map.
     */
    protected void processSources() {
      sourceOutputs.clear();
      for (File sourceFile : compileQueue.keySet()) {
        ResourceMetadata<File> source = sources.get(sourceFile);
        for (ResourceMetadata<File> output : context.getAssociatedOutputs(source)) {
          sourceOutputs.put(sourceFile, output.getResource());
        }
        sources.put(source.getResource(), source.process());
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
      // if (getProc() == Proc.only) {
      // // proc==only cannot be implemented incrementally
      // // changed sources may require types defined in sources that did not change,
      // // which are not available during incremental build without generated .class files
      // for (ResourceMetadata<File> source : sources.values()) {
      // if (!compileQueue.containsKey(source.getResource())) {
      // enqueue(source);
      // }
      // }
      // }

      // incremental compilation loop
      // keep calling the compiler while there are sources in the queue
      while (!compileQueue.isEmpty()) {
        processedQueue.clear();
        processedQueue.addAll(compileQueue.keySet());

        processSources();

        // invoke the compiler
        ICompilationUnit[] compilationUnits = compileQueue.values().toArray(new ICompilationUnit[compileQueue.size()]);
        compileQueue.clear();
        compiler.compile(compilationUnits);
        namingEnvironment.reset();

        deleteStaleOutputs(); // delete stale outputs and enqueue affected sources

        enqueueAffectedSources();
      }

      return processedSources.size();
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

    private void enqueueAffectedSources() throws IOException {
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
      File sourceFile = input.getResource();
      if (processedSources.count(sourceFile) > 10) {
        throw new IllegalStateException("Too many recompiles " + sourceFile);
      }
      processedSources.add(sourceFile);
      compileQueue.put(sourceFile, newSourceFile(sourceFile));
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
      if (!compileQueue.isEmpty()) {
        processSources();

        ICompilationUnit[] compilationUnits = compileQueue.values().toArray(new ICompilationUnit[compileQueue.size()]);
        compiler.compile(compilationUnits);

        deleteStaleOutputs();
      }

      return compileQueue.size();
    }

    @Override
    public void addGeneratedSource(Output<File> generatedSource) {
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
  public int compile() throws MojoExecutionException, IOException {
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
    compilerOptions.suppressWarnings = true;
    compilerOptions.setShowWarnings(isShowWarnings());
    compilerOptions.docCommentSupport = true;

    if (isProcEscalate() && strategy instanceof IncrementalCompilationStrategy) {
      strategy.enqueueAllSources();
      strategy = new FullCompilationStrategy();
    }

    Classpath namingEnvironment = strategy.createClasspath();
    IErrorHandlingPolicy errorHandlingPolicy = DefaultErrorHandlingPolicies.exitAfterAllProblems();
    IProblemFactory problemFactory = ProblemFactory.getProblemFactory(Locale.getDefault());
    Compiler compiler = new Compiler(namingEnvironment, errorHandlingPolicy, compilerOptions, this, problemFactory);
    compiler.options.produceReferenceInfo = true;

    EclipseFileManager fileManager = null;
    try {
      if (!isProcNone()) {
        fileManager = new EclipseFileManager(null, getSourceEncoding());
        fileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, dependencies);
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(getOutputDirectory()));
        fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, Collections.singleton(getGeneratedSourcesDirectory()));

        ProcessingEnvImpl processingEnv = new ProcessingEnvImpl(context, fileManager, getAnnotationProcessorOptions(), compiler, this);

        compiler.annotationProcessorManager = new AnnotationProcessorManager(processingEnv, fileManager, getAnnotationProcessors());
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

  @Override
  public boolean setSources(List<ResourceMetadata<File>> sources) throws IOException {
    return strategy.setSources(sources);
  }

  private CompilationUnit newSourceFile(File source) {
    final String fileName = source.getAbsolutePath();
    final String encoding = getSourceEncoding() != null ? getSourceEncoding().name() : null;
    return new CompilationUnit(null, fileName, encoding, getOutputDirectory().getAbsolutePath(), false);
  }

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

    entries.addAll(dependencypath.getEntries());

    return new Classpath(entries, mutableentries);
  }

  @Override
  public boolean setClasspath(List<File> dependencies, File mainClasses, Set<File> directDependencies) throws IOException {
    this.dependencies = dependencies;

    final List<ClasspathEntry> dependencypath = new ArrayList<ClasspathEntry>();
    final List<File> files = new ArrayList<File>();

    if (isProcOnly()) {
      DependencyClasspathEntry entry = classpathCache.get(getOutputDirectory());
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

    this.dependencypath = new Classpath(dependencypath, null);

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
  public boolean setProcessorpath(List<File> processorpath) throws IOException {
    if (processorpath == null) {
      this.processorpath = dependencies;
    } else if (processorpath.isEmpty()) {
      this.processorpath = Collections.emptyList();
    } else {
      throw new IllegalArgumentException();
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
        MessageSeverity severity = problem.isError() ? MessageSeverity.ERROR : MessageSeverity.WARNING;
        input.addMessage(problem.getSourceLineNumber(), ((DefaultProblem) problem).column, problem.getMessage(), severity, null /* cause */);
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
    return getProc() == Proc.only || getProc() == Proc.onlyEX;
  }

  private boolean isProcNone() {
    return getProc() == Proc.none;
  }

  private boolean isProcEscalate() {
    return getProc() == Proc.procEX || getProc() == Proc.onlyEX;
  }

  private void writeClassFile(Resource<File> input, String relativeStringName, ClassFile classFile) throws IOException {
    final byte[] bytes = classFile.getBytes();
    final File outputFile = new File(getOutputDirectory(), relativeStringName);
    final Output<File> output = context.associatedOutput(input, outputFile);

    boolean significantChange = digestClassFile(output, bytes);

    if (significantChange) {
      // find all sources that reference this type and put them into work queue
      strategy.addDependentsOf(CharOperation.toString(classFile.getCompoundName()));
    }

    final BufferedOutputStream os = new BufferedOutputStream(output.newOutputStream());
    try {
      os.write(bytes);
      os.flush();
    } finally {
      os.close();
    }
  }

  private boolean digestClassFile(Output<File> output, byte[] definition) {
    boolean significantChange = true;
    try {
      ClassFileReader reader = new ClassFileReader(definition, output.getResource().getAbsolutePath().toCharArray());
      byte[] hash = digester.digest(reader);
      if (hash != null) {
        byte[] oldHash = (byte[]) context.setAttribute(output.getResource(), ATTR_CLASS_DIGEST, hash);
        significantChange = oldHash == null || !Arrays.equals(hash, oldHash);
      }
    } catch (ClassFormatException e) {
      // ignore this class
    }
    return significantChange;
  }

  public void addGeneratedSource(Output<File> generatedSource) {
    strategy.addGeneratedSource(generatedSource);
  }

}
