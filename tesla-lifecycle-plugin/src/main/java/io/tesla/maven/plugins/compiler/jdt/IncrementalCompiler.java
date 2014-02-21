package io.tesla.maven.plugins.compiler.jdt;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.spi.CapabilitiesProvider;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInput;
import io.takari.incrementalbuild.spi.DefaultOutput;
import io.takari.incrementalbuild.spi.DefaultOutputMetadata;
import io.tesla.maven.plugins.compiler.AbstractInternalCompiler;
import io.tesla.maven.plugins.compiler.InternalCompilerConfiguration;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

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
import org.eclipse.jdt.internal.compiler.batch.FileSystem.Classpath;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;
import org.eclipse.jdt.internal.core.builder.ProblemFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class IncrementalCompiler extends AbstractInternalCompiler implements ICompilerRequestor {

  private static final String CAPABILITY_TYPE = "jdt.type";
  private static final String CAPABILITY_SIMPLE_TYPE = "jdt.simpleType";

  private static final String KEY_TYPE = "jdt.type";
  private static final String KEY_HASH = "jdt.hash";
  private static final String KEY_CLASSPATH_DIGEST = "jdt.classpath.digest";

  private final DefaultBuildContext<?> context;

  /**
   * Set of ICompilationUnit to be compiled.
   */
  private final Set<ICompilationUnit> compileQueue = new LinkedHashSet<ICompilationUnit>();

  /**
   * Set of File that have already been added to the compile queue.
   */
  private final Set<File> processedSources = new LinkedHashSet<File>();

  private final ClassPathDigester digester = new ClassPathDigester();

  @Inject
  public IncrementalCompiler(InternalCompilerConfiguration mojo, DefaultBuildContext<?> context) {
    super(mojo);

    this.context = (DefaultBuildContext<?>) context;
  }

  @Override
  public void compile() throws MojoExecutionException {
    INameEnvironment namingEnvironment = getClassPath();
    IErrorHandlingPolicy errorHandlingPolicy = DefaultErrorHandlingPolicies.exitAfterAllProblems();
    Map<String, String> args = new HashMap<String, String>();
    // XXX figure out why compiler does not complain if source/target combination is not compatible
    args.put(CompilerOptions.OPTION_TargetPlatform, getTarget()); // support 5/6/7 aliases
    args.put(CompilerOptions.OPTION_Source, getSource()); // support 5/6/7 aliases
    CompilerOptions compilerOptions = new CompilerOptions(args);
    compilerOptions.performMethodsFullRecovery = false;
    compilerOptions.performStatementsRecovery = false;
    IProblemFactory problemFactory = ProblemFactory.getProblemFactory(Locale.getDefault());
    Compiler compiler =
        new Compiler(namingEnvironment, errorHandlingPolicy, compilerOptions, this, problemFactory);
    compiler.options.produceReferenceInfo = true;

    // TODO optimize full build.
    // there is no need to track processed inputs during full build,
    // which saves memory and GC cycles
    // also, if number of sources in the previous build is known, it may be more efficient to
    // rebuild everything after certain % of sources is modified

    try {
      for (String sourceRoot : getSourceRoots()) {
        enqueue(context.registerAndProcessInputs(getSourceFileSet(sourceRoot)));
      }

      for (String type : getChangedDependencyTypes()) {
        for (InputMetadata<File> input : context.getDependentInputs(CAPABILITY_TYPE, type)) {
          enqueue(input.getResource());
        }
      }

      // remove stale outputs and rebuild all sources that reference them
      for (DefaultOutputMetadata output : context.deleteStaleOutputs(false)) {
        enqueueAffectedInputs(output);
      }

      while (!compileQueue.isEmpty()) {
        ICompilationUnit[] sourceFiles =
            compileQueue.toArray(new ICompilationUnit[compileQueue.size()]);
        compileQueue.clear();
        compiler.compile(sourceFiles);
        // TODO this is not necessary, I think
        for (DefaultOutputMetadata output : context.deleteStaleOutputs(false)) {
          enqueueAffectedInputs(output);
        }
        namingEnvironment.cleanup();
      }

      // write type index file
      Multimap<String, byte[]> index = ArrayListMultimap.create();
      for (BuildContext.OutputMetadata<File> output : context.getProcessedOutputs(File.class)) {
        String type = output.getValue(KEY_TYPE, String.class);
        byte[] hash = output.getValue(KEY_HASH, byte[].class);
        index.put(type, hash);
      }
      digester.writeIndex(getOutputDirectory(), index);
    } catch (IOException e) {
      throw new MojoExecutionException("Unexpected IOException during compilation", e);
    }
  }

  /**
   * Returns set of dependency types that changed structurally compared to the previous build,
   * including new and deleted dependency types.
   */
  private Set<String> getChangedDependencyTypes() throws IOException {
    ArrayListMultimap<String, byte[]> newDigest = ArrayListMultimap.create();
    List<String> cp = getClasspathElements();
    for (int i = 1; i < cp.size(); i++) {
      newDigest.putAll(digester.readIndex(new File(cp.get(i))));
    }

    // XXX it is better to track type index for each dependency individually
    // for jar/zip dependencies this will allow index caching

    // little bit hacky, but pom.xml is where classpath is defined, so kinda makes sense
    Input<File> input = context.registerInput(getPom()).process();
    @SuppressWarnings("unchecked")
    Multimap<String, byte[]> oldDigest =
        (Multimap<String, byte[]>) input.setValue(KEY_CLASSPATH_DIGEST, newDigest);

    if (oldDigest == null || oldDigest.isEmpty()) {
      return Collections.emptySet();
    }

    Set<String> result = new HashSet<String>();
    for (Map.Entry<String, Collection<byte[]>> entry : newDigest.asMap().entrySet()) {
      String type = entry.getKey();
      if (!equals(entry.getValue(), oldDigest.get(type))) {
        result.add(type);
      }
    }
    for (Map.Entry<String, Collection<byte[]>> entry : oldDigest.asMap().entrySet()) {
      String type = entry.getKey();
      if (!newDigest.containsKey(type)) {
        result.add(type);
      }
    }

    return result;
  }

  private static boolean equals(Collection<byte[]> a, Collection<byte[]> b) {
    if (a == null) {
      return b == null;
    }
    if (b == null) {
      return false;
    }
    if (a.size() != b.size()) {
      return false;
    }
    // it is exceptionally rare to have more than one hash per type
    // and nested loop is most efficient way to compare two size==1 collections
    for (byte[] value : a) {
      if (!contains(b, value)) {
        return false;
      }
    }
    return true;
  }

  private static boolean contains(Collection<byte[]> collection, byte[] value) {
    for (byte[] other : collection) {
      if (Arrays.equals(value, other)) {
        return true;
      }
    }
    return false;
  }

  private void enqueueAffectedInputs(CapabilitiesProvider output) {
    for (String type : output.getCapabilities(CAPABILITY_TYPE)) {
      for (InputMetadata<File> input : context.getDependentInputs(CAPABILITY_TYPE, type)) {
        enqueue(input.getResource());
      }
    }
    for (String type : output.getCapabilities(CAPABILITY_SIMPLE_TYPE)) {
      for (InputMetadata<File> input : context.getDependentInputs(CAPABILITY_SIMPLE_TYPE, type)) {
        enqueue(input.getResource());
      }
    }
  }

  private void enqueue(Iterable<DefaultInput> sources) {
    for (DefaultInput source : sources) {
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
    final String encoding = null;
    return new CompilationUnit(null, fileName, encoding, getOutputDirectory().getAbsolutePath(),
        false);
  }

  private static class FileSystem extends org.eclipse.jdt.internal.compiler.batch.FileSystem {
    protected FileSystem(Classpath[] paths) {
      super(paths, null);
    }

    @Override
    public void cleanup() {
      // TODO need to reset classpath entry that corresponds to the project output directory
      for (Classpath cpe : classpaths) {
        if (new File(cpe.getPath()).isDirectory()) {
          cpe.reset();
        }
      }
    }
  }

  private INameEnvironment getClassPath() {
    List<FileSystem.Classpath> classpath = new ArrayList<FileSystem.Classpath>();

    classpath.addAll(JavaInstallation.getDefault().getClasspath());

    for (String sourceRoot : getSourceRoots()) {
      // XXX includes/excludes => access rules
      Classpath element = FileSystem.getClasspath(sourceRoot, null, true, null, null);
      if (element != null) {
        classpath.add(element);
      }
    }

    getOutputDirectory().mkdirs(); // XXX does not belong here

    // this also adds outputDirectory
    for (String classpathElement : getClasspathElements()) {
      Classpath element = FileSystem.getClasspath(classpathElement, null, false, null, null);
      if (element != null) {
        classpath.add(element);
      }
    }

    return new FileSystem(classpath.toArray(new FileSystem.Classpath[classpath.size()]));
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
    DefaultInput input = context.registerInput(sourceFile).process();

    if (result.hasProblems()) {
      for (CategorizedProblem problem : result.getProblems()) {
        input.addMessage(problem.getSourceLineNumber(), ((DefaultProblem) problem).column, problem
            .getMessage(), problem.isError()
            ? BuildContext.SEVERITY_ERROR
            : BuildContext.SEVERITY_WARNING, null);
      }
    }

    // track references
    if (result.qualifiedReferences != null) {
      for (char[][] reference : result.qualifiedReferences) {
        input.addRequirement(CAPABILITY_TYPE, CharOperation.toString(reference));
      }
    }
    if (result.simpleNameReferences != null) {
      for (char[] reference : result.simpleNameReferences) {
        input.addRequirement(CAPABILITY_SIMPLE_TYPE, new String(reference));
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

  private void writeClassFile(DefaultInput input, String relativeStringName, ClassFile classFile)
      throws IOException {
    final byte[] bytes = classFile.getBytes();
    final File outputFile = new File(getOutputDirectory(), relativeStringName);
    final DefaultOutput output = input.associateOutput(outputFile);

    final char[][] compoundName = classFile.getCompoundName();
    final String type = CharOperation.toString(compoundName);

    boolean significantChange = true;
    try {
      ClassFileReader reader =
          new ClassFileReader(bytes, outputFile.getAbsolutePath().toCharArray());
      byte[] hash = digester.digestClass(reader);
      if (hash != null) {
        output.setValue(KEY_TYPE, type);
        byte[] oldHash = (byte[]) output.setValue(KEY_HASH, hash);
        significantChange = oldHash == null || !Arrays.equals(hash, oldHash);
      }
    } catch (ClassFormatException e) {
      // this is a bug in jdt compiler if it can't parse class files it just generated
      throw new IllegalStateException("Could not calculate .class file digest", e);
    }

    output.addCapability(CAPABILITY_TYPE, type);
    output.addCapability(CAPABILITY_SIMPLE_TYPE, new String(compoundName[compoundName.length - 1]));

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

}
