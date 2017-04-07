package io.takari.maven.plugins.compile.jdt.classpath;

import static org.eclipse.jdt.internal.compiler.util.SuffixConstants.SUFFIX_STRING_java;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public class SourcepathDirectory extends AbstractClasspathDirectory {

  public static class ClasspathCompilationUnit extends CompilationUnit {
    public ClasspathCompilationUnit(File file, String encoding) {
      super(null /* contents */, file.getAbsolutePath(), encoding, null /* destinationPath */, false /* ignoreOptionalProblems */);
    }
  }

  private final String encoding;

  private SourcepathDirectory(File directory, Set<String> packages, Map<String, File> files, Charset encoding) {
    super(directory, packages, files);
    this.encoding = encoding != null ? encoding.name() : null;
  }

  @Override
  public NameEnvironmentAnswer findType(String packageName, String typeName, AccessRestriction accessRestriction) {
    File javaFile = getFile(packageName, typeName);
    if (javaFile != null) {
      CompilationUnit cu = new ClasspathCompilationUnit(javaFile, encoding);
      return new NameEnvironmentAnswer(cu, accessRestriction);
    }
    return null;
  }

  @Override
  public String toString() {
    return "Sourcepath for directory " + file;
  }

  public static SourcepathDirectory create(File directory, Charset encoding) {
    Set<String> packages = new HashSet<>();
    Map<String, File> files = new HashMap<>();
    scanDirectory(directory, SUFFIX_STRING_java, packages, files);
    return new SourcepathDirectory(directory, packages, files, encoding);
  }

}
