package io.tesla.maven.plugins.compiler.jdt;

import io.tesla.maven.plugins.compiler.AbstractInternalCompiler;
import io.tesla.maven.plugins.compiler.InternalCompilerConfiguration;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

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
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;
import org.eclipse.jdt.internal.core.builder.ProblemFactory;
import org.eclipse.tesla.incremental.BuildContext;

public class IncrementalCompiler extends AbstractInternalCompiler implements ICompilerRequestor {
  private final BuildContext context;

  private final DependencyTracker tracker;

  /**
   * Set of ICompilationUnit to be compiled.
   */
  private final Set<ICompilationUnit> compileQueue = new LinkedHashSet<ICompilationUnit>();

  /**
   * Set of File that have already been added to the compile queue.
   */
  private final Set<File> processedSources = new LinkedHashSet<File>();

  @Inject
  public IncrementalCompiler(InternalCompilerConfiguration mojo, BuildContext context) {
    super(mojo);

    this.context = context;

    DependencyTracker tracker = context.getValue(DependencyTracker.KEY, DependencyTracker.class);
    if (tracker == null) {
      tracker = new DependencyTracker();
      context.setValue(DependencyTracker.KEY, tracker);
    }
    this.tracker = tracker;
  }

  @Override
  public void compile() {
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

    for (String sourceRoot : getSourceRoots()) {
      enqueue(context.registerInputs(getSourceFileSet(sourceRoot)));
    }

    // remove stale outputs and rebuild all sources that reference them
    for (File output : context.deleteStaleOutputs()) {
      enqueue(tracker.removeOutput(output));
    }

    while (!compileQueue.isEmpty()) {
      ICompilationUnit[] sourceFiles =
          compileQueue.toArray(new ICompilationUnit[compileQueue.size()]);
      compileQueue.clear();
      compiler.compile(sourceFiles);
      for (File output : context.deleteStaleOutputs()) {
        enqueue(tracker.removeOutput(output));
      }
      namingEnvironment.cleanup();
    }
  }

  private void enqueue(Collection<File> sources) {
    for (File source : sources) {
      if (source.exists() && processedSources.add(source)) {
        compileQueue.add(newSourceFile(source));
      }
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

    context.addProcessedInput(sourceFile);

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
          writeClassFile(sourceName, relativeStringName, classFile);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }

    if (result.hasProblems()) {
      for (CategorizedProblem problem : result.getProblems()) {
        context.addMessage(sourceFile, problem.getSourceLineNumber(), problem.getSourceStart(),
            problem.getMessage(), problem.isError()
                ? BuildContext.SEVERITY_ERROR
                : BuildContext.SEVERITY_WARNING, null);
      }
    }

    // track references
    final File input = new File(new String(result.getFileName()));
    if (result.qualifiedReferences != null) {
      for (char[][] reference : result.qualifiedReferences) {
        tracker.addReferencedType(input, CharOperation.toString(reference));
      }
    }
    if (result.simpleNameReferences != null) {
      for (char[] reference : result.simpleNameReferences) {
        tracker.addReferencedSimpleName(input, new String(reference));
      }
    }
  }

  private void writeClassFile(String sourceFileName, String relativeStringName, ClassFile classFile)
      throws IOException {
    final File sourceFile = new File(sourceFileName);
    final byte[] bytes = classFile.getBytes();
    final File outputFile = new File(getOutputDirectory(), relativeStringName);
    boolean significantChange = true;
    if (outputFile.canRead()) {
      try {
        ClassFileReader reader = ClassFileReader.read(outputFile);
        // XXX add unit tests for local, anonymous and structural changes
        significantChange =
            !reader.isLocal() && !reader.isAnonymous() && reader.hasStructuralChanges(bytes);
      } catch (ClassFormatException e) {
        significantChange = true;
      }
    }

    if (significantChange) {
      // find all classes that reference this one and put them into work queue
      final char[][] compoundName = classFile.getCompoundName();
      final String providedType = CharOperation.toString(compoundName);
      final String providedSimpleType = new String(compoundName[compoundName.length - 1]);
      enqueue(tracker.addOutput(sourceFile, outputFile, providedType, providedSimpleType));
    }

    final BufferedOutputStream os =
        new BufferedOutputStream(context.newOutputStream(sourceFile, outputFile));
    try {
      os.write(bytes);
      os.flush();
    } finally {
      os.close();
    }
  }

}
