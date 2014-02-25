package io.takari.maven.plugins.compiler.api;

/**
 * The MIT License
 *
 * Copyright (c) 2005, The Codehaus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.codehaus.plexus.util.DirectoryScanner;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl </a>
 * @author <a href="mailto:michal.maczka@dimatics.com">Michal Maczka </a>
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 */
public abstract class AbstractCompiler implements Compiler {
  protected static final String EOL = System.getProperty("line.separator");

  protected static final String PS = System.getProperty("path.separator");

  private CompilerOutputStyle compilerOutputStyle;

  private String inputFileEnding;

  private String outputFileEnding;

  private String outputFile;

  // ----------------------------------------------------------------------
  //
  // ----------------------------------------------------------------------

  protected AbstractCompiler(CompilerOutputStyle compilerOutputStyle, String inputFileEnding, String outputFileEnding, String outputFile) {
    this.compilerOutputStyle = compilerOutputStyle;

    this.inputFileEnding = inputFileEnding;

    this.outputFileEnding = outputFileEnding;

    this.outputFile = outputFile;
  }

  // ----------------------------------------------------------------------
  //
  // ----------------------------------------------------------------------

  public CompilerResult performCompile(CompilerConfiguration configuration) throws CompilerException {
    throw new CompilerNotImplementedException("The performCompile method has not been implemented.");
  }

  @Deprecated
  public List<CompilerError> compile(CompilerConfiguration configuration) throws CompilerException {
    throw new CompilerNotImplementedException("The compile method has not been implemented.");
  }

  public CompilerOutputStyle getCompilerOutputStyle() {
    return compilerOutputStyle;
  }

  public String getInputFileEnding(CompilerConfiguration configuration) throws CompilerException {
    return inputFileEnding;
  }

  public String getOutputFileEnding(CompilerConfiguration configuration) throws CompilerException {
    if (compilerOutputStyle != CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE) {
      throw new RuntimeException("This compiler implementation doesn't have one output file per input file.");
    }

    return outputFileEnding;
  }

  public String getOutputFile(CompilerConfiguration configuration) throws CompilerException {
    if (compilerOutputStyle != CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES) {
      throw new RuntimeException("This compiler implementation doesn't have one output file for all files.");
    }

    return outputFile;
  }

  public boolean canUpdateTarget(CompilerConfiguration configuration) throws CompilerException {
    return true;
  }

  // ----------------------------------------------------------------------
  // Utility Methods
  // ----------------------------------------------------------------------

  public static String getPathString(List<String> pathElements) {
    StringBuilder sb = new StringBuilder();

    for (String pathElement : pathElements) {
      sb.append(pathElement).append(File.pathSeparator);
    }

    return sb.toString();
  }

  protected static Set<String> getSourceFilesForSourceRoot(CompilerConfiguration config, String sourceLocation) {
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(sourceLocation);
    Set<String> includes = config.getIncludes();
    if (includes != null && !includes.isEmpty()) {
      String[] inclStrs = includes.toArray(new String[includes.size()]);
      scanner.setIncludes(inclStrs);
    } else {
      scanner.setIncludes(new String[] {
        "**/*.java"
      });
    }

    Set<String> excludes = config.getExcludes();
    if (excludes != null && !excludes.isEmpty()) {
      String[] exclStrs = excludes.toArray(new String[excludes.size()]);
      scanner.setIncludes(exclStrs);
    }

    scanner.scan();
    String[] sourceDirectorySources = scanner.getIncludedFiles();
    Set<String> sources = new HashSet<String>();

    for (int j = 0; j < sourceDirectorySources.length; j++) {
      File f = new File(sourceLocation, sourceDirectorySources[j]);
      sources.add(f.getPath());
    }

    return sources;
  }

  protected static String[] getSourceFiles(CompilerConfiguration config) {
    Set<String> sources = new HashSet<String>();

    Set<File> sourceFiles = config.getSourceFiles();

    if (sourceFiles != null && !sourceFiles.isEmpty()) {
      for (File sourceFile : sourceFiles) {
        sources.add(sourceFile.getAbsolutePath());
      }
    } else {
      for (String sourceLocation : config.getSourceLocations()) {
        sources.addAll(getSourceFilesForSourceRoot(config, sourceLocation));
      }
    }

    String[] result;

    if (sources.isEmpty()) {
      result = new String[0];
    } else {
      result = (String[]) sources.toArray(new String[sources.size()]);
    }

    return result;
  }

  protected static String makeClassName(String fileName, String sourceDir) throws CompilerException {
    File origFile = new File(fileName);

    String canonical = null;

    if (origFile.exists()) {
      canonical = getCanonicalPath(origFile).replace('\\', '/');
    }

    if (sourceDir != null) {
      String prefix = getCanonicalPath(new File(sourceDir)).replace('\\', '/');

      if (canonical != null) {
        if (canonical.startsWith(prefix)) {
          String result = canonical.substring(prefix.length() + 1, canonical.length() - 5);

          result = result.replace('/', '.');

          return result;
        }
      } else {
        File t = new File(sourceDir, fileName);

        if (t.exists()) {
          String str = getCanonicalPath(t).replace('\\', '/');

          return str.substring(prefix.length() + 1, str.length() - 5).replace('/', '.');
        }
      }
    }

    if (fileName.endsWith(".java")) {
      fileName = fileName.substring(0, fileName.length() - 5);
    }

    fileName = fileName.replace('\\', '.');

    return fileName.replace('/', '.');
  }

  private static String getCanonicalPath(File origFile) throws CompilerException {
    try {
      return origFile.getCanonicalPath();
    } catch (IOException e) {
      throw new CompilerException("Error while getting the canonical path of '" + origFile.getAbsolutePath() + "'.", e);
    }
  }

  /**
   * @deprecated use (String[]) arguments.toArray( new String[ arguments.size() ] ); instead
   */
  protected static String[] toStringArray(List<String> arguments) {
    String[] args = new String[arguments.size()];

    int argLength = arguments.size();

    for (int i = 0; i < argLength; i++) {
      args[i] = arguments.get(i);
    }

    return args;
  }
}
