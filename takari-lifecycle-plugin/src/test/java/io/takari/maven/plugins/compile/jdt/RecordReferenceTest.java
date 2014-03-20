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
import org.junit.Test;

public class RecordReferenceTest {

  @Test
  public void testImport() throws Exception {
    assertReference("Import", //
        "java.lang.Object", "java.util.List");
  }

  @Test
  public void testMissingImport() throws Exception {
    assertReference("MissingImport", //
        "java.lang.Object", "missing.Missing");
  }

  @Test
  public void testImportOnDemand() throws Exception {
    assertReference("ImportOnDemand", //
        "java.lang.Object", "record.reference.Name", "java.lang.Name", //
        "java.util.Name", "java.util.*");
  }

  @Test
  public void testMissingImportOnDemand() throws Exception {
    assertReference("MissingImportOnDemand", //
        "java.lang.Object", "record.reference.Name", "java.lang.Name", "missing.*");
  }

  @Test
  public void testStaticImport() throws Exception {
    assertReference(
        "StaticImport", //
        "java.lang.Object",
        "java.lang.Integer" //
        // somewhat surprisingly, but MAX_VALUE can be a nested type
        // and it can be inherited from anything Integer extends/implements
        , "java.io.Serializable.MAX_VALUE", "java.lang.Comparable.MAX_VALUE",
        "java.lang.Integer.MAX_VALUE", "java.lang.Number.MAX_VALUE", "java.lang.Object.MAX_VALUE" //
    );
  }

  @Test
  public void testTypeParameter() throws Exception {
    assertReference("TypeParameter", //
        "java.lang.Object", "java.lang.Class", "java.io.Serializable"
        // XXX self-reference for type collision detection?
        // 'java.lang.Class', 'java.io.Serializable' can be nested type from 'java' class
        , "java.lang.java", "record.reference.java" //
    );
  }

  @Test
  public void testParameterizedType() throws Exception {
    assertReference("ParameterizedType", //
        "java.lang.Object", "java.io.Serializable" //
        // XXX self-reference for type collision detection?
        // 'java.lang.Serializable' can be nested type from 'java' class
        , "java.lang.java", "record.reference.java" //
        // XXX why type parameter is being resolved
        , "java.lang.T", "record.reference.T" //
    );
  }

  @Test
  public void testImplements() throws Exception {
    assertReference("Implements", //
        "java.lang.Object", "java.lang.Comparable" //
        // needed for 'type collision' error/warning
        , "record.reference.Implements" //
        // 'java.lang.Comparable' can be nested type from 'java' class
        , "java.lang.java", "record.reference.java" //
    );
  }

  @Test
  public void testMethodReturnType() throws Exception {
    assertReference("MethodReturnType", //
        "java.lang.Object", "java.util.List" //
        // XXX self-reference for type collision detection?
        // 'java.util.List' can be nested type from 'java' class
        , "java.lang.java", "record.reference.java" //
    );
  }

  @Test
  public void testMethodParameterType() throws Exception {
    assertReference("MethodParameterType", //
        "java.lang.Object", "java.util.List"
        // needed for 'type collision' error/warning
        , "record.reference.MethodParameterType" //
        // 'java.util.List' can be nested type from 'java' class
        , "java.lang.java", "record.reference.java" //
    );
  }

  private void assertReference(String source, String... expectedReferences) {
    IErrorHandlingPolicy errorHandlingPolicy = DefaultErrorHandlingPolicies.exitAfterAllProblems();
    Map<String, String> args = new HashMap<String, String>();
    // XXX figure out how to reuse source/target check from jdt
    // org.eclipse.jdt.internal.compiler.batch.Main.validateOptions(boolean)
    args.put(CompilerOptions.OPTION_Compliance, "1.7");
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
        if (result.packageReferences != null) {
          for (char[][] reference : result.packageReferences) {
            actualReferences.add(CharOperation.toString(reference) + ".*");
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
