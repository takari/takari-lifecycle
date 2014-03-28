package io.takari.maven.plugins.compile.jdt;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInput;
import io.takari.incrementalbuild.spi.DefaultInputMetadata;
import io.takari.incrementalbuild.spi.DefaultOutput;
import io.takari.incrementalbuild.spi.DefaultOutputMetadata;
import io.takari.maven.plugins.compile.AbstractCompiler;
import io.takari.maven.plugins.compile.jdt.classpath.Classpath;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.JavaInstallation;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.maven.artifact.Artifact;
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
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;
import org.eclipse.jdt.internal.core.builder.ProblemFactory;

import com.google.common.base.Stopwatch;

public class CompilerJdt extends AbstractCompiler implements ICompilerRequestor {

  private static final String CAPABILITY_TYPE = "jdt.type";

  private static final String CAPABILITY_PACKAGE = "jdt.package";

  private static final String KEY_TYPE = "jdt.type";

  private static final String KEY_HASH = "jdt.hash";

  private static final String KEY_TYPEINDEX = "jdt.typeIndex";

  private static final String KEY_PACKAGEINDEX = "jdt.packageIndex";

  /**
   * {@code KEY_TYPE} value used for local and anonymous types and also class files with
   * corrupted/unsupported format.
   */
  private static final String TYPE_NOTYPE = ".";

  private Classpath dependencypath;

  /**
   * Set of ICompilationUnit to be compiled.
   */
  private final Set<ICompilationUnit> compileQueue = new LinkedHashSet<ICompilationUnit>();

  /**
   * Set of File that have already been added to the compile queue.
   */
  private final Set<File> processedSources = new LinkedHashSet<File>();

  private final ClassfileDigester digester = new ClassfileDigester();

  private final ClasspathEntryCache classpathCache;

  public CompilerJdt(DefaultBuildContext<?> context, ClasspathEntryCache classpathCache) {
    super(context);
    this.classpathCache = classpathCache;
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
    CompilerOptions compilerOptions = new CompilerOptions(args);
    compilerOptions.performMethodsFullRecovery = false;
    compilerOptions.performStatementsRecovery = false;
    compilerOptions.verbose = isVerbose();
    compilerOptions.suppressWarnings = true;
    IProblemFactory problemFactory = ProblemFactory.getProblemFactory(Locale.getDefault());
    Classpath namingEnvironment = createClasspath();
    Compiler compiler =
        new Compiler(namingEnvironment, errorHandlingPolicy, compilerOptions, this, problemFactory);
    compiler.options.produceReferenceInfo = true;

    // TODO optimize full build.
    // there is no need to track processed inputs during full build,
    // which saves memory and GC cycles
    // also, if number of sources in the previous build is known, it may be more efficient to
    // rebuild everything after certain % of sources is modified

    // keep calling the compiler while there are sources in the queue
    while (!compileQueue.isEmpty()) {
      ICompilationUnit[] sourceFiles =
          compileQueue.toArray(new ICompilationUnit[compileQueue.size()]);
      compileQueue.clear();
      compiler.compile(sourceFiles);
      namingEnvironment.reset();
    }

    HashMap<String, byte[]> index = new HashMap<String, byte[]>();
    HashMap<String, Boolean> packageIndex = new HashMap<String, Boolean>();
    for (DefaultInputMetadata<File> input : context.getRegisteredInputs()) {
      for (String type : input.getRequiredCapabilities(CAPABILITY_TYPE)) {
        if (!index.containsKey(type)) {
          index.put(type, digest(dependencypath, type));
        }
      }
      for (String pkg : input.getRequiredCapabilities(CAPABILITY_PACKAGE)) {
        if (!packageIndex.containsKey(pkg)) {
          packageIndex.put(pkg, isPackage(dependencypath, pkg));
        }
      }
    }
    DefaultInput<File> pom = context.registerInput(getPom()).process();
    pom.setValue(KEY_TYPEINDEX, index);
    pom.setValue(KEY_PACKAGEINDEX, packageIndex);
  }

  private byte[] digest(INameEnvironment namingEnvironment, String type) {
    NameEnvironmentAnswer answer =
        namingEnvironment.findType(CharOperation.splitOn('.', type.toCharArray()));
    if (answer != null && answer.isBinaryType()) {
      return digester.digest(answer.getBinaryType());
    }
    return null;
  }

  private boolean isPackage(INameEnvironment namingEnvironment, String pkg) {
    return namingEnvironment.isPackage(CharOperation.splitOn('.', pkg.toCharArray()), null);
  }

  @Override
  public boolean setSources(List<File> sources) throws IOException {
    enqueue(context.registerAndProcessInputs(sources));

    // remove stale outputs and rebuild all sources that reference them
    for (DefaultOutputMetadata output : context.deleteStaleOutputs(false)) {
      enqueueAffectedInputs(output);
    }

    return !compileQueue.isEmpty();
  }

  private void enqueueAffectedInputs(DefaultOutputMetadata output) {
    for (String type : output.getCapabilities(CAPABILITY_TYPE)) {
      for (InputMetadata<File> input : context.getDependentInputs(CAPABILITY_TYPE, type)) {
        enqueue(input.getResource());
      }
    }
  }

  private void enqueue(Iterable<DefaultInput<File>> sources) {
    for (DefaultInput<File> source : sources) {
      enqueue(source.getResource());
    }
  }

  private void enqueue(File sourceFile) {
    if (processedSources.add(sourceFile)) {
      compileQueue.add(newSourceFile(sourceFile));
    }
  }

  private CompilationUnit newSourceFile(File source) {
    final String fileName = source.getAbsolutePath();
    final String encoding = getSourceEncoding() != null ? getSourceEncoding().name() : null;
    return new CompilationUnit(null, fileName, encoding, getOutputDirectory().getAbsolutePath(),
        false);
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

    OutputDirectoryClasspathEntry output =
        new OutputDirectoryClasspathEntry(getOutputDirectory(), false, null);
    entries.add(output);
    mutableentries.add(output);

    entries.addAll(dependencypath.getEntries());

    return new Classpath(entries, mutableentries);
  }

  @Override
  public boolean setClasspath(List<Artifact> dependencies) throws IOException {
    final List<ClasspathEntry> dependencypath = new ArrayList<ClasspathEntry>();

    for (Artifact dependency : dependencies) {
      ClasspathEntry entry = classpathCache.get(dependency.getFile());
      if (entry != null) {
        dependencypath.add(entry);
      }
    }

    Stopwatch stopwatch = new Stopwatch().start();
    long typecount = 0, packagecount = 0;

    this.dependencypath = new Classpath(dependencypath, null);

    @SuppressWarnings("unchecked")
    HashMap<String, byte[]> index =
        context.registerInput(getPom()).getValue(KEY_TYPEINDEX, HashMap.class);
    if (index != null) {
      for (Map.Entry<String, byte[]> entry : index.entrySet()) {
        typecount++;
        String type = entry.getKey();
        byte[] hash = digest(this.dependencypath, type);
        if (!Arrays.equals(entry.getValue(), hash)) {
          for (DefaultInputMetadata<File> metadata : context.getDependentInputs(CAPABILITY_TYPE,
              type)) {
            enqueue(metadata.getResource());
          }
        }
      }
    }

    @SuppressWarnings("unchecked")
    HashMap<String, Boolean> packageIndex =
        context.registerInput(getPom()).getValue(KEY_PACKAGEINDEX, HashMap.class);
    if (packageIndex != null) {
      for (Map.Entry<String, Boolean> entry : packageIndex.entrySet()) {
        packagecount++;
        String pkg = entry.getKey();
        boolean isPackage = isPackage(this.dependencypath, pkg);
        if (isPackage != entry.getValue()) {
          for (DefaultInputMetadata<File> metadata : context.getDependentInputs(CAPABILITY_PACKAGE,
              pkg)) {
            enqueue(metadata.getResource());
          }
        }
      }
    }

    log.debug("Verified {} types and {} packages in {} ms", typecount, packagecount,
        stopwatch.elapsed(TimeUnit.MILLISECONDS));

    return !compileQueue.isEmpty();
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

    if (result.hasProblems()) {
      for (CategorizedProblem problem : result.getProblems()) {
        input.addMessage(problem.getSourceLineNumber(), ((DefaultProblem) problem).column, problem
            .getMessage(), problem.isError()
            ? BuildContext.Severity.ERROR
            : BuildContext.Severity.WARNING, null);
      }
    }

    // track references
    if (result.qualifiedReferences != null) {
      for (char[][] reference : result.qualifiedReferences) {
        input.addRequirement(CAPABILITY_TYPE, CharOperation.toString(reference));
      }
    }
    if (result.packageReferences != null) {
      for (char[][] reference : result.packageReferences) {
        // TODO get rid of java.lang.* reference
        input.addRequirement(CAPABILITY_PACKAGE, CharOperation.toString(reference));
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
  }

  private void writeClassFile(DefaultInput<File> input, String relativeStringName,
      ClassFile classFile) throws IOException {
    final byte[] bytes = classFile.getBytes();
    final File outputFile = new File(getOutputDirectory(), relativeStringName);
    final DefaultOutput output = input.associateOutput(outputFile);

    String type = CharOperation.toString(classFile.getCompoundName());

    boolean significantChange = digestClassFile(output, bytes, type);

    if (significantChange) {
      // find all classes that reference this one and put them into work queue
      enqueueAffectedInputs(output);
    }

    final BufferedOutputStream os = new BufferedOutputStream(output.newOutputStream());
    try {
      os.write(bytes);
      os.flush();
    } finally {
      os.close();
    }
  }

  private boolean digestClassFile(DefaultOutput output, byte[] definition, String type) {
    boolean significantChange = true;
    try {
      ClassFileReader reader =
          new ClassFileReader(definition, output.getResource().getAbsolutePath().toCharArray());
      byte[] hash = digester.digest(reader);
      if (hash != null) {
        output.setValue(KEY_TYPE, type);
        output.addCapability(CAPABILITY_TYPE, type);
        byte[] oldHash = (byte[]) output.setValue(KEY_HASH, hash);
        significantChange = oldHash == null || !Arrays.equals(hash, oldHash);
      } else {
        output.setValue(KEY_TYPE, TYPE_NOTYPE);
      }
    } catch (ClassFormatException e) {
      output.setValue(KEY_TYPE, TYPE_NOTYPE);
    }
    return significantChange;
  }

  @Override
  public void skipCompilation() {
    // unlike javac, jdt compiler tracks input-output association
    // this allows BuildContext to automatically carry-over output metadata
  }
}
