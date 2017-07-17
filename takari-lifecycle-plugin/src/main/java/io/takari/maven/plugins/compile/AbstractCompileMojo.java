/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.tools.JavaFileObject.Kind;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.maven.plugins.compile.javac.CompilerJavacLauncher;
import io.takari.maven.plugins.exportpackage.ExportPackageMojo;

public abstract class AbstractCompileMojo extends AbstractMojo {

  private static final String DEFAULT_COMPILER_LEVEL = "1.7";

  // I much prefer slf4j over plexus logger api
  private final Logger log = LoggerFactory.getLogger(getClass());

  public static enum Proc {
    proc, only, none,

    /**
     * Same as {@link #proc}
     * 
     * @deprecated use {@link #proc}
     */
    procEX,

    /**
     * Same as {@link #only}
     * 
     * @deprecated use {@link #only}
     */
    onlyEX
  }

  public static enum Debug {
    all, none, source, lines, vars;
  }

  public static enum AccessRulesViolation {
    error, ignore;
  }

  public static enum Sourcepath {
    disable, reactorDependencies;
  }

  /**
   * The -encoding argument for the Java compiler.
   */
  @Parameter(property = "encoding", defaultValue = "${project.build.sourceEncoding}")
  private String encoding;

  /**
   * The -source argument for the Java compiler.
   */
  @Parameter(property = "maven.compiler.source", defaultValue = DEFAULT_COMPILER_LEVEL)
  private String source;

  /**
   * The -target argument for the Java compiler. The default depends on the value of {@code source} as defined in javac documentation.
   * 
   * @see http://docs.oracle.com/javase/6/docs/technotes/tools/solaris/javac.html
   */
  @Parameter(property = "maven.compiler.target")
  private String target;

  /**
   * The compiler id of the compiler to use, one of {@code javac}, {@code forked-javac} or {@code jdt}.
   */
  @Parameter(property = "maven.compiler.compilerId", defaultValue = "javac")
  protected String compilerId;

  /**
   * Initial size, in megabytes, of the memory allocation pool, ex. "64", "64m" if {@link #fork} is set to <code>true</code>.
   */
  @Parameter(property = "maven.compiler.meminitial")
  private String meminitial;

  /**
   * Sets the maximum size, in megabytes, of the memory allocation pool, ex. "128", "128m" if {@link #fork} is set to <code>true</code>.
   */
  @Parameter(property = "maven.compiler.maxmem")
  private String maxmem;

  /**
   * <p>
   * Sets whether annotation processing is performed or not. This parameter is required if annotation processors are present on compile classpath.
   * </p>
   * <p>
   * Allowed values are:
   * </p>
   * <ul>
   * <li><code>proc</code> - both compilation and annotation processing are performed at the same time.</li>
   * <li><code>none</code> - no annotation processing is performed.</li>
   * <li><code>only</code> - only annotation processing is done, no compilation.</li>
   * </ul>
   */
  @Parameter
  protected Proc proc;

  /**
   * <p>
   * Names of annotation processors to run. If not set, the default annotation processors discovery process applies.
   * </p>
   */
  @Parameter
  protected String[] annotationProcessors;

  /**
   * Annotation processors options
   */
  @Parameter
  private Map<String, String> annotationProcessorOptions;

  /**
   * Set to <code>true</code> to show messages about what the compiler is doing.
   */
  @Parameter(property = "maven.compiler.verbose", defaultValue = "false")
  private boolean verbose;

  /**
   * Set to <code>true</code> to store formal parameter names of constructors and methods in the generated class file so that the method java.lang.reflect.Executable.getParameters from the Reflection
   * API can retrieve them.
   */
  @Parameter(defaultValue = "false")
  private boolean parameters;

  /**
   * Sets whether generated class files include debug information or not.
   * <p>
   * Allowed values
   * <ul>
   * <li><strong>all</strong> or <strong>true</strong> Generate all debugging information, including local variables. This is the default.</li>
   * <li><strong>none</strong> or <strong>false</strong> Do not generate any debugging information.</li>
   * <li>Comma-separated list of
   * <ul>
   * <li><strong>source</strong> Source file debugging information.</li>
   * <li><strong>lines</strong> Line number debugging information.</li>
   * <li><strong>vars</strong> Local variable debugging information.</li>
   * </ul>
   * </li>
   * </ul>
   */
  @Parameter(property = "maven.compiler.debug", defaultValue = "all")
  private String debug;

  /**
   * Set to <code>true</code> to show compilation warnings.
   */
  @Parameter(property = "maven.compiler.showWarnings", defaultValue = "false")
  private boolean showWarnings;

  /**
   * Sets "transitive dependency reference" policy violation action.
   * <p>
   * If {@code error}, only references to types defined in dependencies declared in project pom.xml file (or inherited from parent pom.xml) are allowed. References to types defined in transitive
   * dependencies will be result in compilation errors. If {@code ignore} (the default) references to types defined in all project dependencies are allowed.
   *
   * @see <a href="http://takari.io/book/40-lifecycle.html#the-takari-lifecycle">The Takari Lifecycle</a> documentation for more details
   * @since 1.9
   */
  @Parameter(defaultValue = "ignore")
  private AccessRulesViolation transitiveDependencyReference;

  /**
   * Sets "private package reference" policy violation action.
   * <p>
   * If {@code error}, only references to types defined in dependency exported packages are allowed. References to types defined in private packages will be result in compilation errors. If
   * {@code ignore} (the default) references to types in all packages are allowed.
   *
   * @see ExportPackageMojo
   * @see <a href="http://takari.io/book/40-lifecycle.html#the-takari-lifecycle">The Takari Lifecycle</a> documentation for more details
   * @since 1.9
   */
  @Parameter(defaultValue = "ignore")
  private AccessRulesViolation privatePackageReference;

  /**
   * Controls compilation sourcepath. If set to {@code disable}, compilation sourcepath will be empty. If set to {@code reactorProjects}, compilation sourcepath will be set to compile source roots (or
   * test compile source roots) of dependency projects of the same reactor build. The default is {@code reactorProjects} if {@code proc=only}, otherwise the default is {@code disable}.
   * 
   * <p/>
   * The main usecase is {@code proc:only} annotation processing bound to generate-sources build phase. During {@code mvn clean generate-sources} execution, the reactor dependencies classes are not
   * available and referenced types can only be resolved from java sources.
   * 
   * @see http://docs.oracle.com/javase/8/docs/technotes/tools/unix/javac.html#BHCJJJAJ
   * @since 1.12
   */
  @Parameter
  protected Sourcepath sourcepath;

  //

  @Parameter(defaultValue = "${project.file}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  private File pom;

  @Parameter(defaultValue = "${project.basedir}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  private File basedir;

  @Parameter(defaultValue = "${project.build.directory}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  private File buildDirectory;

  @Parameter(defaultValue = "${plugin.pluginArtifact}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  private Artifact pluginArtifact;

  @Parameter(defaultValue = "${project.dependencyArtifacts}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  private Set<Artifact> directDependencies;

  @Parameter(defaultValue = "${project}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected MavenProject project;

  @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected RepositorySystemSession repositorySession;

  //

  @Component
  private Map<String, AbstractCompiler> compilers;

  @Component
  private CompilerBuildContext context;

  @Component
  private ReactorProjects reactorProjects;

  @Component
  private ProcessorpathResolver processorpathResolver;

  public Charset getSourceEncoding() {
    return encoding == null ? null : Charset.forName(encoding);
  }

  private List<ResourceMetadata<File>> getSources() throws IOException, MojoExecutionException {
    List<ResourceMetadata<File>> sources = new ArrayList<ResourceMetadata<File>>();
    StringBuilder msg = new StringBuilder();
    for (String sourcePath : getSourceRoots()) {
      File sourceRoot = new File(sourcePath);
      msg.append("\n").append(sourcePath);
      if (!sourceRoot.isDirectory()) {
        msg.append("\n   does not exist or not a directory, skiped");
        continue;
      }
      // TODO this is a bug in project model, includes/excludes should be per sourceRoot
      Set<String> includes = getIncludes();
      if (includes == null || includes.isEmpty()) {
        includes = Collections.singleton("**/*.java");
      } else {
        for (String include : includes) {
          Set<String> illegal = new LinkedHashSet<>();
          if (!include.endsWith(Kind.SOURCE.extension)) {
            illegal.add(include);
          }
          if (!illegal.isEmpty()) {
            throw new MojoExecutionException(String.format("<includes> patterns must end with %s. Illegal patterns: %s", Kind.SOURCE.extension, illegal.toString()));
          }
        }
      }
      Set<String> excludes = getExcludes();
      int sourceCount = 0;
      for (ResourceMetadata<File> source : context.registerSources(sourceRoot, includes, excludes)) {
        sources.add(source);
        sourceCount++;
      }
      if (log.isDebugEnabled()) {
        msg.append("\n   includes=").append(includes.toString());
        msg.append(" excludes=").append(excludes != null ? excludes.toString() : "[]");
        msg.append(" matched=").append(sourceCount);
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("Compile source roots:{}", msg);
    }
    return sources;
  }

  protected Set<File> getDirectDependencies() {
    Set<File> result = new LinkedHashSet<>();
    for (Artifact artofact : directDependencies) {
      result.add(artofact.getFile());
    }
    return result;
  }

  protected abstract Set<String> getSourceRoots();

  protected abstract Set<String> getIncludes();

  protected abstract Set<String> getExcludes();

  protected abstract File getOutputDirectory();

  protected abstract List<Artifact> getClasspathArtifacts();

  protected abstract File getGeneratedSourcesDirectory();

  protected abstract boolean isSkip();

  protected abstract File getMainOutputDirectory();

  protected abstract Set<String> getMainSourceRoots();

  protected abstract List<Dependency> getProcessorpathDependencies();

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    Stopwatch stopwatch = Stopwatch.createStarted();

    if (isSkip()) {
      log.info("Skipping compilation");
      context.markSkipExecution();
      return;
    }

    final AbstractCompiler compiler = compilers.get(compilerId);
    if (compiler == null) {
      throw new MojoExecutionException("Unsupported compilerId" + compilerId);
    }

    try {
      final List<ResourceMetadata<File>> sources = getSources();

      if (!sources.isEmpty()) {
        mkdirs(getOutputDirectory());
      }

      final List<File> classpath = getClasspath();
      final List<File> processorpath = getProcessorpath();
      Proc proc = getEffectiveProc(classpath, processorpath);

      if (proc != Proc.none && !sources.isEmpty()) {
        mkdirs(getGeneratedSourcesDirectory());
      }

      compiler.setOutputDirectory(getOutputDirectory());
      compiler.setSource(source);
      compiler.setTarget(getTarget(target, source));
      compiler.setProc(proc);
      compiler.setGeneratedSourcesDirectory(getGeneratedSourcesDirectory());
      compiler.setAnnotationProcessors(annotationProcessors);
      compiler.setAnnotationProcessorOptions(annotationProcessorOptions);
      compiler.setVerbose(verbose);
      compiler.setParameters(parameters);
      compiler.setPom(pom);
      compiler.setSourceEncoding(getSourceEncoding());
      compiler.setDebug(parseDebug(debug));
      compiler.setShowWarnings(showWarnings);
      compiler.setTransitiveDependencyReference(transitiveDependencyReference);
      compiler.setPrivatePackageReference(privatePackageReference);

      if (compiler instanceof CompilerJavacLauncher) {
        ((CompilerJavacLauncher) compiler).setBasedir(basedir);
        ((CompilerJavacLauncher) compiler).setJar(pluginArtifact.getFile());
        ((CompilerJavacLauncher) compiler).setBuildDirectory(buildDirectory);
        ((CompilerJavacLauncher) compiler).setMeminitial(meminitial);
        ((CompilerJavacLauncher) compiler).setMaxmem(maxmem);
      }

      boolean sourcesChanged = compiler.setSources(sources);
      boolean classpathChanged = compiler.setClasspath(classpath, getMainOutputDirectory(), getDirectDependencies());
      boolean sourcepathChanged = compiler.setSourcepath(getSourcepath(proc), toFileSet(getSourceRoots()));
      boolean processorpathChanged = proc != Proc.none ? compiler.setProcessorpath(processorpath) : false;

      if (sourcesChanged || classpathChanged || sourcepathChanged || processorpathChanged) {
        log.info("Compiling {} sources to {}", sources.size(), getOutputDirectory());
        int compiled = compiler.compile();
        log.info("Compiled {} out of {} sources ({} ms)", compiled, sources.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
      } else {
        compiler.skipCompile();
        log.info("Skipped compilation, all {} classes are up to date", sources.size());
      }

      if (proc != Proc.none && !sources.isEmpty()) {
        addGeneratedSources(project);
      }

    } catch (IOException e) {
      throw new MojoExecutionException("Could not compile project", e);
    }
  }

  private static Set<File> toFileSet(Set<String> paths) {
    Set<File> files = new LinkedHashSet<>();
    paths.forEach(path -> files.add(new File(path)));
    return files;
  }

  private List<File> getClasspath() {
    List<File> classpath = new ArrayList<File>();
    for (Artifact artifact : getClasspathArtifacts()) {
      File file = artifact.getFile();
      if (file != null) {
        classpath.add(file);
      }
    }
    return classpath;
  }

  private List<File> getSourcepath(Proc proc) throws MojoExecutionException {
    if (sourcepath == Sourcepath.disable) {
      return Collections.emptyList();
    }
    if (sourcepath == null && proc != Proc.only) {
      return Collections.emptyList();
    }

    if (privatePackageReference != AccessRulesViolation.ignore) {
      // dependency export-packages are calculated for classes folder, information is not available for source folders
      throw new MojoExecutionException("<sourcepath> parameter is not compatible with <privatePackageReference>");
    }

    Set<File> sourcepath = new LinkedHashSet<>();
    for (String sourceRoot : Iterables.concat(getSourceRoots(), getMainSourceRoots())) {
      addIfExists(sourcepath, sourceRoot);
    }
    List<Artifact> unsupportedDependencies = new ArrayList<>();
    for (Artifact artifact : getClasspathArtifacts()) {
      MavenProject other = reactorProjects.get(artifact);
      if (other != null) {
        if (artifact.getClassifier() != null && !"tests".equals(artifact.getClassifier())) {
          // generally, can't tell anything about classified artifacts
          unsupportedDependencies.add(artifact);
        } else {
          // TODO assert encoding is the same
          for (String sourceRoot : getSourceRoots(other, artifact)) {
            addIfExists(sourcepath, sourceRoot);
          }
        }
      }
    }

    if (!unsupportedDependencies.isEmpty()) {
      throw new MojoExecutionException("Unsupported <sourcepath> classified reactor dependencies: " + unsupportedDependencies);
    }

    if (log.isDebugEnabled()) {
      StringBuilder msg = new StringBuilder();
      for (File element : sourcepath) {
        msg.append("\n   ").append(element.getAbsolutePath());
      }
      log.debug("Compile sourcepath: {} entries{}", sourcepath.size(), msg.toString());
    }

    return ImmutableList.copyOf(sourcepath);
  }

  private static void addIfExists(Set<File> sourcepath, String path) {
    File file = new File(path);
    if (file.exists()) {
      sourcepath.add(file);
    }
  }

  private Collection<String> getSourceRoots(MavenProject other, Artifact artifact) {
    Set<String> sourceRoots = new LinkedHashSet<>();
    // always add main sources, they may be needed to resolve types referenced from test-jar dependencies
    // TODO does this mean testCompile sourcepath is wider than classpath in some cases?
    sourceRoots.addAll(other.getCompileSourceRoots());
    if ("test-jar".equals(artifact.getType())) {
      sourceRoots.addAll(other.getTestCompileSourceRoots());
    }
    return sourceRoots;
  }

  private Proc getEffectiveProc(List<File> classpath, List<File> processorpath) {
    Proc proc = this.proc;
    if (proc == null) {
      Multimap<File, String> processors = TreeMultimap.create();
      for (File jar : processorpath != null ? processorpath : classpath) {
        if (jar.isFile()) {
          try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("META-INF/services/javax.annotation.processing.Processor");
            if (entry != null) {
              try (Reader r = new InputStreamReader(zip.getInputStream(entry), Charsets.UTF_8)) {
                processors.putAll(jar, CharStreams.readLines(r));
              }
            }
          } catch (IOException e) {
            // ignore, compiler won't be able to use this jar either
          }
        } else if (jar.isDirectory()) {
          try {
            processors.putAll(jar, Files.readLines(new File(jar, "META-INF/services/javax.annotation.processing.Processor"), Charsets.UTF_8));
          } catch (IOException e) {
            // ignore, compiler won't be able to use this jar either
          }
        }
      }
      if (!processors.isEmpty()) {
        StringBuilder msg = new StringBuilder("<proc> must be one of 'none', 'only' or 'proc'. Processors found: ");
        for (File jar : processors.keySet()) {
          msg.append("\n   ").append(jar).append(" ").append(processors.get(jar));
        }
        throw new IllegalArgumentException(msg.toString());
      }
      proc = Proc.none;
    }
    if (proc == Proc.procEX) {
      log.warn("proc=procEX is deprecated, use proc=proc.");
      proc = Proc.proc;
    } else if (proc == Proc.onlyEX) {
      log.warn("proc=onlyEX is deprecated, use proc=only.");
      proc = Proc.only;
    }
    return proc;
  }

  private static String getTarget(String target, String source) {
    if (target != null) {
      return target;
    }
    if (source != null) {
      if ("1.2".equals(source) || "1.3".equals(source)) {
        return "1.4";
      }
      return source;
    }
    return DEFAULT_COMPILER_LEVEL;
  }

  private static Set<Debug> parseDebug(String debug) {
    Set<Debug> result = new HashSet<AbstractCompileMojo.Debug>();
    StringTokenizer st = new StringTokenizer(debug, ",");
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      Debug keyword;
      if ("true".equalsIgnoreCase(token)) {
        keyword = Debug.all;
      } else if ("false".equalsIgnoreCase(token)) {
        keyword = Debug.none;
      } else {
        keyword = Debug.valueOf(token);
      }
      result.add(keyword);
    }
    if (result.size() > 1 && (result.contains(Debug.all) || result.contains(Debug.none))) {
      throw new IllegalArgumentException("'all' and 'none' must be used alone: " + debug);
    }
    return result;
  }

  private File mkdirs(File dir) throws MojoExecutionException {
    if (!dir.isDirectory() && !dir.mkdirs()) {
      throw new MojoExecutionException("Could not create directory " + dir);
    }
    return dir;
  }

  /**
   * Returns possibly empty list of explicitly specified processorpath entries. Returns {@code null} if the project classpath should be searched for annotation processors.
   */
  protected List<File> getProcessorpath() throws MojoExecutionException {
    List<Dependency> dependencies = getProcessorpathDependencies();
    if (dependencies == null) {
      return null;
    }
    return processorpathResolver.resolve(repositorySession, project, dependencies);
  }

  protected abstract void addGeneratedSources(MavenProject project);
}
