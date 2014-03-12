package io.takari.maven.plugins.compile.jdt;

import io.takari.incrementalbuild.*;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.spi.*;
import io.takari.maven.plugins.compile.AbstractCompileMojo;
import io.takari.maven.plugins.compile.ClassfileDigester;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.Compiler;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

public class CompilerJdt implements ICompilerRequestor {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final String CAPABILITY_TYPE = "jdt.type";
  private static final String CAPABILITY_SIMPLE_TYPE = "jdt.simpleType";

  private final DefaultBuildContext<?> context;

  private final AbstractCompileMojo mojo;

  private final String sourceEncoding;

  /**
   * Set of ICompilationUnit to be compiled.
   */
  private final Set<ICompilationUnit> compileQueue = new LinkedHashSet<ICompilationUnit>();

  /**
   * Set of File that have already been added to the compile queue.
   */
  private final Set<File> processedSources = new LinkedHashSet<File>();

  private final ClassfileDigester digester = new ClassfileDigester();

  @Inject
  public CompilerJdt(AbstractCompileMojo mojo, DefaultBuildContext<?> context) {
    this.mojo = mojo;
    this.context = context;
    this.sourceEncoding = mojo.getSourceEncoding() != null ? mojo.getSourceEncoding().name() : null;
  }

  public void compile(List<File> sources, Set<String> changedDependencyTypes)
      throws MojoExecutionException {
    INameEnvironment namingEnvironment = getClassPath();
    IErrorHandlingPolicy errorHandlingPolicy = DefaultErrorHandlingPolicies.exitAfterAllProblems();
    Map<String, String> args = new HashMap<String, String>();
    // XXX figure out why compiler does not complain if source/target combination is not compatible
    args.put(CompilerOptions.OPTION_TargetPlatform, mojo.getTarget()); // support 5/6/7 aliases
    args.put(CompilerOptions.OPTION_Source, mojo.getSource()); // support 5/6/7 aliases
    if (mojo.getSourceEncoding() != null) {
      // TODO not sure this is necessary, #newSourceFile handles source encoding already
      args.put(CompilerOptions.OPTION_Encoding, mojo.getSourceEncoding().name());
    }
    CompilerOptions compilerOptions = new CompilerOptions(args);
    compilerOptions.performMethodsFullRecovery = false;
    compilerOptions.performStatementsRecovery = false;
    compilerOptions.verbose = mojo.isVerbose();
    IProblemFactory problemFactory = ProblemFactory.getProblemFactory(Locale.getDefault());
    Compiler compiler =
        new Compiler(namingEnvironment, errorHandlingPolicy, compilerOptions, this, problemFactory);
    compiler.options.produceReferenceInfo = true;

    // TODO optimize full build.
    // there is no need to track processed inputs during full build,
    // which saves memory and GC cycles
    // also, if number of sources in the previous build is known, it may be more efficient to
    // rebuild everything after certain % of sources is modified

    Stopwatch stopwatch = new Stopwatch().start();

    try {
      enqueue(context.registerAndProcessInputs(sources));

      for (String type : changedDependencyTypes) {
        for (InputMetadata<File> input : context.getDependentInputs(CAPABILITY_TYPE, type)) {
          enqueue(input.getResource());
        }
        int idx = type.lastIndexOf('.');
        if (idx > 0) {
          String simpleType = type.substring(idx + 1);
          for (InputMetadata<File> input : context.getDependentInputs(CAPABILITY_SIMPLE_TYPE,
              simpleType)) {
            enqueue(input.getResource());
          }
        }
      }

      // remove stale outputs and rebuild all sources that reference them
      for (DefaultOutputMetadata output : context.deleteStaleOutputs(false)) {
        enqueueAffectedInputs(output);
      }

      // keep calling the compiler while there are sources in the queue
      while (!compileQueue.isEmpty()) {
        ICompilationUnit[] sourceFiles =
            compileQueue.toArray(new ICompilationUnit[compileQueue.size()]);
        compileQueue.clear();
        compiler.compile(sourceFiles);
        namingEnvironment.cleanup();
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Unexpected IOException during compilation", e);
    }

    log.info("Compilation time {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));
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
    final String encoding = sourceEncoding;
    return new CompilationUnit(null, fileName, encoding, mojo.getOutputDirectory()
        .getAbsolutePath(), false);
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

    for (String sourceRoot : mojo.getSourceRoots()) {
      // TODO why do I need this here? unit test or take out
      // XXX includes/excludes => access rules
      Classpath element = FileSystem.getClasspath(sourceRoot, null, true, null, null);
      if (element != null) {
        classpath.add(element);
      }
    }

    classpath.add(FileSystem.getClasspath(mojo.getOutputDirectory().getAbsolutePath(), null, false,
        null, null));

    // this also adds outputDirectory
    for (Artifact classpathElement : mojo.getCompileArtifacts()) {
      String path = classpathElement.getFile().getAbsolutePath();
      Classpath element = FileSystem.getClasspath(path, null, false, null, null);
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

  private void writeClassFile(DefaultInput<File> input, String relativeStringName,
      ClassFile classFile) throws IOException {
    final byte[] bytes = classFile.getBytes();
    final File outputFile = new File(mojo.getOutputDirectory(), relativeStringName);
    final DefaultOutput output = input.associateOutput(outputFile);

    final char[][] compoundName = classFile.getCompoundName();
    final String type = CharOperation.toString(compoundName);

    boolean significantChange = digestClassFile(output, bytes);

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

  private static final String KEY_TYPE = "jdt.type";
  private static final String KEY_HASH = "jdt.hash";
  /**
   * {@code KEY_TYPE} value used for local and anonymous types and also class files with
   * corrupted/unsupported format.
   */
  private static final String TYPE_NOTYPE = ".";

  public boolean digestClassFile(Output<File> output, byte[] definition) {
    boolean significantChange = true;
    try {
      ClassFileReader reader =
          new ClassFileReader(definition, output.getResource().getAbsolutePath().toCharArray());
      String type = new String(CharOperation.replaceOnCopy(reader.getName(), '/', '.'));
      byte[] hash = digester.digest(reader);
      if (hash != null) {
        output.setValue(KEY_TYPE, type);
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

}
