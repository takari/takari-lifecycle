package adhoc;

import io.takari.maven.plugins.compile.jdt.JavaInstallation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.core.builder.ProblemFactory;

public class CompleMain {
  public static void main(String[] args) {
    System.setSecurityManager(null);

    compile();
  }

  public static void compile() {
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
    ICompilerRequestor callback = new ICompilerRequestor() {
      @Override
      public void acceptResult(CompilationResult result) {
        if (result.qualifiedReferences != null) {
          for (char[][] reference : result.qualifiedReferences) {
            System.out.println("Q " + CharOperation.toString(reference));
          }
        }
        if (result.simpleNameReferences != null) {
          for (char[] reference : result.simpleNameReferences) {
            System.out.println("S " + new String(reference));
          }
        }
        if (result.rootReferences != null) {
          for (char[] reference : result.rootReferences) {
            System.out.println("R " + new String(reference));
          }
        }
        System.out.println(result);
      }
    };
    Compiler compiler =
        new Compiler(namingEnvironment, errorHandlingPolicy, compilerOptions, callback,
            problemFactory);
    compiler.options.produceReferenceInfo = true;
    ICompilationUnit[] sourceFiles = {new CompilationUnit(null, //
        "/workspaces/tesla-dev/adhoc/src/adhoc/TypeParameter.java", //
        "UTF-8", //
        "/workspaces/tesla-dev/adhoc/bin", //
        false)};
    compiler.compile(sourceFiles);
  }

  private static INameEnvironment getClasspath() {
    final List<FileSystem.Classpath> classpath = new ArrayList<FileSystem.Classpath>();
    classpath.addAll(JavaInstallation.getDefault().getClasspath());
    return new FileSystem(classpath.toArray(new FileSystem.Classpath[classpath.size()]), null) {
      @Override
      public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
        return super.findType(typeName, packageName);
      }

      @Override
      public NameEnvironmentAnswer findType(char[][] compoundName) {
        return super.findType(compoundName);
      }
    };
  }

}
