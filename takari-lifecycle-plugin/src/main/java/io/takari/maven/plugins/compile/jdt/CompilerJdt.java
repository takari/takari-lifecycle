package io.takari.maven.plugins.compile.jdt;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInput;
import io.takari.incrementalbuild.spi.DefaultOutput;
import io.takari.incrementalbuild.spi.DefaultOutputMetadata;
import io.takari.maven.plugins.compile.AbstractCompileMojo;
import io.takari.maven.plugins.compile.AbstractCompiler;

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
import org.eclipse.jdt.internal.compiler.batch.FileSystem.Classpath;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;
import org.eclipse.jdt.internal.core.builder.ProblemFactory;

public class CompilerJdt extends AbstractCompiler implements ICompilerRequestor {

  private static final String CAPABILITY_TYPE = "jdt.type";

  private static final String KEY_TYPE = "jdt.type";

  private static final String KEY_HASH = "jdt.hash";

  private static final String KEY_TYPEINDEX = "jdt.typeIndex";

  /**
   * {@code KEY_TYPE} value used for local and anonymous types and also class files with
   * corrupted/unsupported format.
   */
  private static final String TYPE_NOTYPE = ".";

  private final String sourceEncoding;

  final List<FileSystem.Classpath> classpath = new ArrayList<FileSystem.Classpath>();

  /**
   * Set of ICompilationUnit to be compiled.
   */
  private final Set<ICompilationUnit> compileQueue = new LinkedHashSet<ICompilationUnit>();

  /**
   * Set of File that have already been added to the compile queue.
   */
  private final Set<File> processedSources = new LinkedHashSet<File>();

  private final ClassfileDigester digester = new ClassfileDigester();

  public CompilerJdt(AbstractCompileMojo mojo, DefaultBuildContext<?> context) {
    super(context, mojo);
    this.sourceEncoding = mojo.getSourceEncoding() != null ? mojo.getSourceEncoding().name() : null;
  }

  @Override
  public void compile() throws MojoExecutionException, IOException {
    IErrorHandlingPolicy errorHandlingPolicy = DefaultErrorHandlingPolicies.exitAfterAllProblems();
    Map<String, String> args = new HashMap<String, String>();
    // XXX figure out how to reuse source/target check from jdt
    // org.eclipse.jdt.internal.compiler.batch.Main.validateOptions(boolean)
    args.put(CompilerOptions.OPTION_TargetPlatform, config.getTarget()); // support 5/6/7 aliases
    args.put(CompilerOptions.OPTION_Source, config.getSource()); // support 5/6/7 aliases
    if (config.getSourceEncoding() != null) {
      // TODO not sure this is necessary, #newSourceFile handles source encoding already
      args.put(CompilerOptions.OPTION_Encoding, config.getSourceEncoding().name());
    }
    CompilerOptions compilerOptions = new CompilerOptions(args);
    compilerOptions.performMethodsFullRecovery = false;
    compilerOptions.performStatementsRecovery = false;
    compilerOptions.verbose = config.isVerbose();
    IProblemFactory problemFactory = ProblemFactory.getProblemFactory(Locale.getDefault());
    INameEnvironment namingEnvironment =
        new FileSystem(classpath.toArray(new FileSystem.Classpath[classpath.size()]));
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
      namingEnvironment.cleanup();
    }

    DefaultInput<File> pom = context.registerInput(config.getPom()).process();
    index.update(namingEnvironment);
    pom.setValue(KEY_TYPEINDEX, index.toByteArray());

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
    final String encoding = sourceEncoding;
    return new CompilationUnit(null, fileName, encoding, config.getOutputDirectory()
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

  @Override
  public boolean setClasspath(List<Artifact> dependencies) throws IOException {

    // XXX detect change!
    classpath.addAll(JavaInstallation.getDefault().getClasspath());

    for (String sourceRoot : config.getSourceRoots()) {
      // TODO why do I need this here? unit test or take out
      // XXX includes/excludes => access rules
      Classpath element = FileSystem.getClasspath(sourceRoot, null, true, null, null);
      if (element != null) {
        classpath.add(element);
      }
    }

    classpath.add(FileSystem.getClasspath(config.getOutputDirectory().getAbsolutePath(), null,
        false, null, null));

    for (Artifact classpathElement : config.getCompileArtifacts()) {
      String path = classpathElement.getFile().getAbsolutePath();
      Classpath element = FileSystem.getClasspath(path, null, false, null, null);
      if (element != null) {
        classpath.add(element);
      }
    }

    // XXX only look in dependencies!
    INameEnvironment namingEnvironment =
        new FileSystem(classpath.toArray(new FileSystem.Classpath[classpath.size()]));

    for (String filename : index.getAffectedSources(namingEnvironment)) {
      enqueue(new File(filename));
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
    final File outputFile = new File(config.getOutputDirectory(), relativeStringName);
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
