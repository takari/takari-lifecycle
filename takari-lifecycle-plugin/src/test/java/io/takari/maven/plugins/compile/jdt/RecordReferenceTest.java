package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.batch.FileSystem;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.core.builder.ProblemFactory;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class RecordReferenceTest {

  @Test
  public void testImport() throws Exception {
    assertReference("Import", //
        "java.lang.Object", "java.util.List");
  }

  @Test
  public void testImportOnDemand() throws Exception {
    assertReference("ImportOnDemand", //
        "java.lang.Object", "record.reference.Name", "java.lang.Name", "java.util.Name");
  }

  @Test
  @Ignore("missing package tracking is not implemented")
  public void testMissingImportOnDemand() throws Exception {
    assertReference("MissingImportOnDemand", //
        "java.lang.Object", "record.reference.Name", "java.lang.Name", "missing.Name");
  }

  @Test
  @Ignore("tracks way too much, needs fixing")
  public void testStaticImport() throws Exception {
    assertReference("StaticImport", //
        "java.lang.Object", "java.lang.Integer");
  }

  @Test
  @Ignore("tracks way too much, needs fixing")
  public void testTypeParameter() throws Exception {
    assertReference("TypeParameter", //
        "java.lang.Object", "java.lang.Class", "java.io.Serializable");
  }

  @Test
  public void testParameterizedType() throws Exception {
    assertReference("ParameterizedType", //
        "java.lang.Object", "java.lang.Class", "java.io.Serializable");
  }

  @Test
  @Ignore("tracks way too much, needs fixing")
  public void testImplements() throws Exception {
    assertReference("Implements", //
        "java.lang.Object", "java.lang.Comparable");
  }

  @Test
  @Ignore("tracks way too much, needs fixing")
  public void testMethodReturnType() throws Exception {
    assertReference("MethodReturnType", //
        "java.lang.Object", "java.util.List");
  }

  @Test
  @Ignore("tracks way too much, needs fixing")
  public void testMethodParameterType() throws Exception {
    assertReference("MethodParameterType", //
        "java.lang.Object", "java.util.List");
  }

  private void assertReference(String source, String... expectedReferences) {
    IErrorHandlingPolicy errorHandlingPolicy = DefaultErrorHandlingPolicies.exitAfterAllProblems();
    Map<String, String> args = new HashMap<String, String>();
    // XXX figure out how to reuse source/target check from jdt
    // org.eclipse.jdt.internal.compiler.batch.Main.validateOptions(boolean)
    args.put(CompilerOptions.OPTION_TargetPlatform, "1.7");
    args.put(CompilerOptions.OPTION_Source, "1.7");
    CompilerOptions compilerOptions = new CompilerOptions(args);
    compilerOptions.performMethodsFullRecovery = false;
    compilerOptions.performStatementsRecovery = false;
    IProblemFactory problemFactory = ProblemFactory.getProblemFactory(Locale.getDefault());
    INameEnvironment namingEnvironment = getClasspath();
    final Set<String> actualReferences = new LinkedHashSet<String>();
    ICompilerRequestor callback = new ICompilerRequestor() {
      @Override
      public void acceptResult(CompilationResult result) {
        if (result.qualifiedReferences != null) {
          for (char[][] reference : result.qualifiedReferences) {
            actualReferences.add(CharOperation.toString(reference));
          }
        }
      }
    };
    Compiler compiler =
        new Compiler(namingEnvironment, errorHandlingPolicy, compilerOptions, callback,
            problemFactory);
    compiler.options.produceReferenceInfo = true;
    String sourcePath =
        new File("src/test/projects/compile-jdt-record-reference", source + ".java")
            .getAbsolutePath();
    ICompilationUnit[] sourceFiles = {new CompilationUnit(null, //
        sourcePath, //
        "UTF-8", //
        null, // destinationPath
        false // ignoreOptionalProblems
    )};
    compiler.compile(sourceFiles);

    Assert.assertEquals(toString(Arrays.asList(expectedReferences)), toString(actualReferences));
  }

  private String toString(Collection<String> strings) {
    TreeSet<String> sorted = new TreeSet<String>(strings);
    StringBuilder sb = new StringBuilder();
    for (String string : sorted) {
      if (sb.length() > 0) {
        sb.append("\n");
      }
      sb.append(string);
    }
    return sb.toString();
  }

  private static INameEnvironment getClasspath() {
    final List<FileSystem.Classpath> classpath = new ArrayList<FileSystem.Classpath>();
    classpath.addAll(JavaInstallation.getDefault().getClasspath());
    return new FileSystem(classpath.toArray(new FileSystem.Classpath[classpath.size()]), null) {};
  }

}
