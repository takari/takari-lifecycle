/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInput;
import io.takari.incrementalbuild.spi.DefaultInputMetadata;
import io.takari.incrementalbuild.spi.DefaultOutput;
import io.takari.incrementalbuild.spi.DefaultOutputMetadata;
import io.takari.maven.plugins.compile.AbstractCompileMojo.AccessRulesViolation;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Debug;
import io.takari.maven.plugins.compile.AbstractCompiler;
import io.takari.maven.plugins.compile.jdt.classpath.Classpath;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.JavaInstallation;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

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

  private Classpath dependencypath;

  /**
   * Set of ICompilationUnit to be compiled.
   */
  private final Set<ICompilationUnit> compileQueue = new LinkedHashSet<ICompilationUnit>();

  /**
   * Set of File that have already been added to the compile queue.
   */
  private final Set<File> processedSources = new LinkedHashSet<File>();

  private final Set<String> rootNames = new LinkedHashSet<String>();

  private final Set<String> qualifiedNames = new LinkedHashSet<String>();

  private final Set<String> simpleNames = new LinkedHashSet<String>();

  private final ClassfileDigester digester = new ClassfileDigester();

  private final ClasspathEntryCache classpathCache;

  private final ClasspathDigester classpathDigester;

  @Inject
  public CompilerJdt(DefaultBuildContext<?> context, ClasspathEntryCache classpathCache, ClasspathDigester classpathDigester) {
    super(context);
    this.classpathCache = classpathCache;
    this.classpathDigester = classpathDigester;
  }

  @Override
  public void compile() throws MojoExecutionException, IOException {
    IErrorHandlingPolicy errorHandlingPolicy = DefaultErrorHandlingPolicies.exitAfterAllProblems();
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
          warningThreshold = IrritantSet.ALL;
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
    IProblemFactory problemFactory = ProblemFactory.getProblemFactory(Locale.getDefault());
    Classpath namingEnvironment = createClasspath();
    Compiler compiler = new Compiler(namingEnvironment, errorHandlingPolicy, compilerOptions, this, problemFactory);
    compiler.options.produceReferenceInfo = true;

    // TODO optimize full build.
    // there is no need to track processed inputs during full build,
    // which saves memory and GC cycles
    // also, if number of sources in the previous build is known, it may be more efficient to
    // rebuild everything after certain % of sources is modified

    // keep calling the compiler while there are sources in the queue
    while (!compileQueue.isEmpty()) {
      ICompilationUnit[] sourceFiles = compileQueue.toArray(new ICompilationUnit[compileQueue.size()]);
      compileQueue.clear();
      compiler.compile(sourceFiles);
      namingEnvironment.reset();
      enqueueAffectedSources();
    }
  }

  @Override
  public boolean setSources(List<InputMetadata<File>> sources) throws IOException {
    for (InputMetadata<File> source : sources) {
      if (source.getStatus() != ResourceStatus.UNMODIFIED) {
        enqueue(source.process().getResource());
      }
    }

    // remove stale outputs and rebuild all sources that reference them
    for (DefaultOutputMetadata output : context.deleteStaleOutputs(false)) {
      addDependentsOf(getJavaType(output));
    }

    enqueueAffectedSources();

    return !compileQueue.isEmpty();
  }

  private String getJavaType(DefaultOutputMetadata output) {
    String outputDirectory = getOutputDirectory().getAbsolutePath();
    String path = output.getResource().getAbsolutePath();
    if (!path.startsWith(outputDirectory) || !path.endsWith(".class")) {
      return null;
    }
    path = path.substring(outputDirectory.length(), path.length() - ".class".length());
    if (path.startsWith(File.separator)) {
      path = path.substring(1);
    }
    return path.replace(File.separatorChar, '.');
  }

  private void enqueueAffectedSources() throws IOException {
    for (InputMetadata<File> input : context.getRegisteredInputs(File.class)) {
      final File resource = input.getResource();
      if (!processedSources.contains(resource) && resource.canRead()) {
        ReferenceCollection references = input.getAttribute(ATTR_REFERENCES, ReferenceCollection.class);
        if (references != null && references.includes(qualifiedNames, simpleNames, rootNames)) {
          enqueue(resource);
        }
      }
    }

    qualifiedNames.clear();
    simpleNames.clear();
    rootNames.clear();
  }

  private void enqueue(File sourceFile) {
    if (processedSources.add(sourceFile)) {
      compileQueue.add(newSourceFile(sourceFile));
    }
  }

  private CompilationUnit newSourceFile(File source) {
    final String fileName = source.getAbsolutePath();
    final String encoding = getSourceEncoding() != null ? getSourceEncoding().name() : null;
    return new CompilationUnit(null, fileName, encoding, getOutputDirectory().getAbsolutePath(), false);
  }

  private Classpath createClasspath() throws IOException {
    final List<ClasspathEntry> entries = new ArrayList<ClasspathEntry>();
    final List<MutableClasspathEntry> mutableentries = new ArrayList<MutableClasspathEntry>();

    // XXX detect change!
    for (File file : JavaInstallation.getDefault().getClasspath()) {
      ClasspathEntry entry = classpathCache.get(file);
      if (entry != null) {
        entries.add(entry);
      }
    }

    CompileQueueClasspathEntry queueEntry = new CompileQueueClasspathEntry(compileQueue);
    entries.add(queueEntry);
    mutableentries.add(queueEntry);

    OutputDirectoryClasspathEntry output = new OutputDirectoryClasspathEntry(getOutputDirectory());
    entries.add(output);
    mutableentries.add(output);

    entries.addAll(dependencypath.getEntries());

    return new Classpath(entries, mutableentries);
  }

  @Override
  public boolean setClasspath(List<File> dependencies, Set<File> directDependencies) throws IOException {
    final List<ClasspathEntry> dependencypath = new ArrayList<ClasspathEntry>();
    final List<File> files = new ArrayList<File>();

    for (File dependency : dependencies) {
      ClasspathEntry entry = classpathCache.get(dependency);
      if (entry != null) {
        if (getAccessRulesViolation() == AccessRulesViolation.error && !directDependencies.contains(dependency)) {
          entry = new ForbiddenClasspathEntry(entry);
        }
        dependencypath.add(entry);
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

    Stopwatch stopwatch = new Stopwatch().start();
    long typecount = 0, packagecount = 0;

    HashMap<String, byte[]> digest = classpathDigester.digestDependencies(files);

    DefaultInputMetadata<File> metadata = context.registerInput(getPom());
    @SuppressWarnings("unchecked")
    Map<String, byte[]> oldDigest = (Map<String, byte[]>) metadata.getAttribute(ATTR_CLASSPATH_DIGEST, Serializable.class);

    boolean changed = false;

    if (oldDigest != null) {
      Set<String> changedPackages = new HashSet<String>();

      for (Map.Entry<String, byte[]> entry : digest.entrySet()) {
        String type = entry.getKey();
        byte[] hash = entry.getValue();
        if (!Arrays.equals(hash, oldDigest.get(type))) {
          changed = true;
          addDependentsOf(type);
        }
        changedPackages.add(getPackage(type));
      }

      for (String oldType : oldDigest.keySet()) {
        if (!digest.containsKey(oldType)) {
          changed = true;
          addDependentsOf(oldType);
        }
        changedPackages.remove(getPackage(oldType));
      }

      for (String changedPackage : changedPackages) {
        addDependentsOf(changedPackage);
      }
    } else {
      changed = true;
    }

    if (changed) {
      metadata.process().setAttribute(ATTR_CLASSPATH_DIGEST, digest);
    }

    log.debug("Verified {} types and {} packages in {} ms", typecount, packagecount, stopwatch.elapsed(TimeUnit.MILLISECONDS));

    enqueueAffectedSources();

    return !compileQueue.isEmpty();
  }

  private String getPackage(String type) {
    int idx = type.lastIndexOf('.');
    return idx > 0 ? type.substring(0, idx) : null;
  }

  @Override
  public void acceptResult(CompilationResult result) {
    if (result == null) {
      return; // ah?
    }
    final String sourceName = new String(result.getFileName());
    final File sourceFile = new File(sourceName);

    processedSources.add(sourceFile);

    // JDT may decide to compile more sources than it was asked to in some cases
    // always register and process sources with build context
    DefaultInput<File> input = context.registerInput(sourceFile).process();

    // track type references
    input.setAttribute(ATTR_REFERENCES, new ReferenceCollection(result.rootReferences, result.qualifiedReferences, result.simpleNameReferences));

    if (result.hasProblems()) {
      for (CategorizedProblem problem : result.getProblems()) {
        input.addMessage(problem.getSourceLineNumber(), ((DefaultProblem) problem).column, problem.getMessage(), problem.isError() ? BuildContext.Severity.ERROR : BuildContext.Severity.WARNING, null);
      }
    }

    if (!result.hasErrors()) {
      for (ClassFile classFile : result.getClassFiles()) {
        try {
          char[] filename = classFile.fileName();
          int length = filename.length;
          char[] relativeName = new char[length + 6];
          System.arraycopy(filename, 0, relativeName, 0, length);
          System.arraycopy(SuffixConstants.SUFFIX_class, 0, relativeName, length, 6);
          CharOperation.replace(relativeName, '/', File.separatorChar);
          String relativeStringName = new String(relativeName);
          writeClassFile(input, relativeStringName, classFile);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    // XXX double check affected sources are recompiled when this source has errors
  }

  private void writeClassFile(DefaultInput<File> input, String relativeStringName, ClassFile classFile) throws IOException {
    final byte[] bytes = classFile.getBytes();
    final File outputFile = new File(getOutputDirectory(), relativeStringName);
    final DefaultOutput output = input.associateOutput(outputFile);

    boolean significantChange = digestClassFile(output, bytes);

    if (significantChange) {
      // find all sources that reference this type and put them into work queue
      addDependentsOf(CharOperation.toString(classFile.getCompoundName()));
    }

    final BufferedOutputStream os = new BufferedOutputStream(output.newOutputStream());
    try {
      os.write(bytes);
      os.flush();
    } finally {
      os.close();
    }
  }

  private boolean digestClassFile(DefaultOutput output, byte[] definition) {
    boolean significantChange = true;
    try {
      ClassFileReader reader = new ClassFileReader(definition, output.getResource().getAbsolutePath().toCharArray());
      byte[] hash = digester.digest(reader);
      if (hash != null) {
        byte[] oldHash = (byte[]) output.setAttribute(ATTR_CLASS_DIGEST, hash);
        significantChange = oldHash == null || !Arrays.equals(hash, oldHash);
      }
    } catch (ClassFormatException e) {
      // ignore this class
    }
    return significantChange;
  }

  private void addDependentsOf(String typeOrPackage) {
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

  @Override
  public void skipCompilation() {
    // unlike javac, jdt compiler tracks input-output association
    // this allows BuildContext to automatically carry-over output metadata
  }
}
