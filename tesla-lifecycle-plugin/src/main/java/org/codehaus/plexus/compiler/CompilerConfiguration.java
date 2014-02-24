package org.codehaus.plexus.compiler;

/**
 * The MIT License
 *
 * Copyright (c) 2004, The Codehaus
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jdcasey
 */
public class CompilerConfiguration {
  private String outputLocation;

  private List<String> classpathEntries = new LinkedList<String>();

  // ----------------------------------------------------------------------
  // Source Files
  // ----------------------------------------------------------------------

  private Set<File> sourceFiles = new HashSet<File>();

  private List<String> sourceLocations = new LinkedList<String>();

  private Set<String> includes = new HashSet<String>();

  private Set<String> excludes = new HashSet<String>();

  // ----------------------------------------------------------------------
  // Compiler Settings
  // ----------------------------------------------------------------------

  private boolean debug;

  private String debugLevel;

  private boolean showWarnings = true;

  private boolean showDeprecation;

  private String sourceVersion;

  private String targetVersion;

  private String sourceEncoding;

  private Map<String, String> customCompilerArguments = new LinkedHashMap<String, String>();

  private boolean fork;

  private boolean optimize;

  private String meminitial;

  private String maxmem;

  private String executable;

  private File workingDirectory;

  private String compilerVersion;

  private boolean verbose = false;

  /**
   * A build temporary directory, eg target/.
   * <p/>
   * Used by the compiler implementation to put temporary files.
   */
  private File buildDirectory;

  /**
   * Used to control the name of the output file when compiling a set of
   * sources to a single file.
   */
  private String outputFileName;

  /**
   * in jdk 1.6+, used to hold value of the -s path parameter.
   */
  private File generatedSourcesDirectory;

  /**
   * value of the -proc parameter in jdk 1.6+
   */
  private String proc;

  /**
   * -processor parameters in jdk 1.6+
   */
  private String[] annotationProcessors;

  /**
   * default value {@link CompilerReuseStrategy.ReuseCreated}
   *
   * @since 1.9
   */
  private CompilerReuseStrategy compilerReuseStrategy = CompilerReuseStrategy.ReuseCreated;

  /**
   * force usage of old JavacCompiler even if javax.tools is detected
   * @since 2.0
   */
  private boolean forceJavacCompilerUse = false;

  private List<String> compilerArgs;

  // ----------------------------------------------------------------------
  //
  // ----------------------------------------------------------------------

  public void setOutputLocation(String outputLocation) {
    this.outputLocation = outputLocation;
  }

  public String getOutputLocation() {
    return outputLocation;
  }

  // ----------------------------------------------------------------------
  // Class path
  // ----------------------------------------------------------------------

  public void addClasspathEntry(String classpathEntry) {
    classpathEntries.add(classpathEntry);
  }

  public void setClasspathEntries(List<String> classpathEntries) {
    if (classpathEntries == null) {
      this.classpathEntries = Collections.emptyList();
    } else {
      this.classpathEntries = new LinkedList<String>(classpathEntries);
    }
  }

  public List<String> getClasspathEntries() {
    return Collections.unmodifiableList(classpathEntries);
  }

  // ----------------------------------------------------------------------
  // Source files
  // ----------------------------------------------------------------------

  public void setSourceFiles(Set<File> sourceFiles) {
    if (sourceFiles == null) {
      this.sourceFiles = Collections.emptySet();
    } else {
      this.sourceFiles = new HashSet<File>(sourceFiles);
    }
  }

  public Set<File> getSourceFiles() {
    return sourceFiles;
  }

  public void addSourceLocation(String sourceLocation) {
    sourceLocations.add(sourceLocation);
  }

  public void setSourceLocations(List<String> sourceLocations) {
    if (sourceLocations == null) {
      this.sourceLocations = Collections.emptyList();
    } else {
      this.sourceLocations = new LinkedList<String>(sourceLocations);
    }
  }

  public List<String> getSourceLocations() {
    return Collections.unmodifiableList(sourceLocations);
  }

  public void addInclude(String include) {
    includes.add(include);
  }

  public void setIncludes(Set<String> includes) {
    if (includes == null) {
      this.includes = Collections.emptySet();
    } else {
      this.includes = new HashSet<String>(includes);
    }
  }

  public Set<String> getIncludes() {
    return Collections.unmodifiableSet(includes);
  }

  public void addExclude(String exclude) {
    excludes.add(exclude);
  }

  public void setExcludes(Set<String> excludes) {
    if (excludes == null) {
      this.excludes = Collections.emptySet();
    } else {
      this.excludes = new HashSet<String>(excludes);
    }
  }

  public Set<String> getExcludes() {
    return Collections.unmodifiableSet(excludes);
  }

  // ----------------------------------------------------------------------
  // Compiler Settings
  // ----------------------------------------------------------------------

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebugLevel(String debugLevel) {
    this.debugLevel = debugLevel;
  }

  public String getDebugLevel() {
    return debugLevel;
  }

  public void setShowWarnings(boolean showWarnings) {
    this.showWarnings = showWarnings;
  }

  public boolean isShowWarnings() {
    return showWarnings;
  }

  public boolean isShowDeprecation() {
    return showDeprecation;
  }

  public void setShowDeprecation(boolean showDeprecation) {
    this.showDeprecation = showDeprecation;
  }

  public String getSourceVersion() {
    return sourceVersion;
  }

  public void setSourceVersion(String sourceVersion) {
    this.sourceVersion = sourceVersion;
  }

  public String getTargetVersion() {
    return targetVersion;
  }

  public void setTargetVersion(String targetVersion) {
    this.targetVersion = targetVersion;
  }

  public String getSourceEncoding() {
    return sourceEncoding;
  }

  public void setSourceEncoding(String sourceEncoding) {
    this.sourceEncoding = sourceEncoding;
  }

  public void addCompilerCustomArgument(String customArgument, String value) {
    customCompilerArguments.put(customArgument, value);
  }

  /**
   * @deprecated will be removed in 2.X use #getCustomCompilerArgumentsAsMap
   * @return
   */
  public LinkedHashMap<String, String> getCustomCompilerArguments() {
    return new LinkedHashMap<String, String>(customCompilerArguments);
  }

  /**
   * @deprecated will be removed in 2.X use #setCustomCompilerArgumentsAsMap
   * @param customCompilerArguments
   */
  public void setCustomCompilerArguments(LinkedHashMap<String, String> customCompilerArguments) {
    if (customCompilerArguments == null) {
      this.customCompilerArguments = new LinkedHashMap<String, String>();
    } else {
      this.customCompilerArguments = customCompilerArguments;
    }
  }

  public Map<String, String> getCustomCompilerArgumentsAsMap() {
    return new LinkedHashMap<String, String>(customCompilerArguments);
  }

  public void setCustomCompilerArgumentsAsMap(Map<String, String> customCompilerArguments) {
    if (customCompilerArguments == null) {
      this.customCompilerArguments = new LinkedHashMap<String, String>();
    } else {
      this.customCompilerArguments = customCompilerArguments;
    }
  }

  public boolean isFork() {
    return fork;
  }

  public void setFork(boolean fork) {
    this.fork = fork;
  }

  public String getMeminitial() {
    return meminitial;
  }

  public void setMeminitial(String meminitial) {
    this.meminitial = meminitial;
  }

  public String getMaxmem() {
    return maxmem;
  }

  public void setMaxmem(String maxmem) {
    this.maxmem = maxmem;
  }

  public String getExecutable() {
    return executable;
  }

  public void setExecutable(String executable) {
    this.executable = executable;
  }

  public File getWorkingDirectory() {
    return workingDirectory;
  }

  public void setWorkingDirectory(File workingDirectory) {
    this.workingDirectory = workingDirectory;
  }

  public File getBuildDirectory() {
    return buildDirectory;
  }

  public void setBuildDirectory(File buildDirectory) {
    this.buildDirectory = buildDirectory;
  }

  public String getOutputFileName() {
    return outputFileName;
  }

  public void setOutputFileName(String outputFileName) {
    this.outputFileName = outputFileName;
  }

  public boolean isOptimize() {
    return optimize;
  }

  public void setOptimize(boolean optimize) {
    this.optimize = optimize;
  }

  public String getCompilerVersion() {
    return compilerVersion;
  }

  public void setCompilerVersion(String compilerVersion) {
    this.compilerVersion = compilerVersion;
  }

  public boolean isVerbose() {
    return verbose;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  public void setProc(String proc) {
    this.proc = proc;
  }

  public void setGeneratedSourcesDirectory(File generatedSourcesDirectory) {
    this.generatedSourcesDirectory = generatedSourcesDirectory;
  }

  public File getGeneratedSourcesDirectory() {
    return generatedSourcesDirectory;
  }

  public String getProc() {
    return proc;
  }

  public void setAnnotationProcessors(String[] annotationProcessors) {
    this.annotationProcessors = annotationProcessors;
  }

  public String[] getAnnotationProcessors() {
    return annotationProcessors;
  }

  public CompilerReuseStrategy getCompilerReuseStrategy() {
    return compilerReuseStrategy;
  }

  public void setCompilerReuseStrategy(CompilerReuseStrategy compilerReuseStrategy) {
    this.compilerReuseStrategy = compilerReuseStrategy;
  }

  /**
   * Re-use strategy of the compiler (implement for java only).
   */
  public enum CompilerReuseStrategy {
    /**
     * Always reuse the same.
     * <b>Default strategy.</b>
     */
    ReuseSame("reuseSame"),
    /**
     * Re-create a new compiler for each use.
     */
    AlwaysNew("alwaysNew"),
    /**
     * Re-use already created compiler, create new one if non already exists.
     * <b>Will mimic a kind of pool to prevent different threads use the same.</b>
     */
    ReuseCreated("reuseCreated");

    private String strategy;

    CompilerReuseStrategy(String strategy) {
      this.strategy = strategy;
    }

    public String getStrategy() {
      return strategy;
    }

    @Override
    public String toString() {
      return "CompilerReuseStrategy:" + this.strategy;
    }
  }

  public boolean isForceJavacCompilerUse() {
    return forceJavacCompilerUse;
  }

  public void setForceJavacCompilerUse(boolean forceJavacCompilerUse) {
    this.forceJavacCompilerUse = forceJavacCompilerUse;
  }
  
  public List<String> getCompilerArgs() {
    return compilerArgs;
  }

  public void setCompilerArgs(List<String> compilerArgs) {
    this.compilerArgs = compilerArgs;
  }  
}
