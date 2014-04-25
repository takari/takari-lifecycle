package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "compile", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class CompileMojo extends AbstractCompileMojo {
  /**
   * The source directories containing the sources to be compiled.
   */
  @Parameter(defaultValue = "${project.compileSourceRoots}", readonly = true, required = true)
  private List<String> compileSourceRoots;

  /**
   * A list of inclusion filters for the compiler.
   */
  @Parameter
  private Set<String> includes = new HashSet<String>();

  /**
   * A list of exclusion filters for the compiler.
   */
  @Parameter
  private Set<String> excludes = new HashSet<String>();

  /**
   * Project classpath.
   */
  @Parameter(defaultValue = "${project.compileArtifacts}", readonly = true, required = true)
  @Incremental(configuration = Configuration.ignore)
  private List<Artifact> compileArtifacts;

  /**
   * The directory for compiled classes.
   */
  @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
  private File outputDirectory;

  /**
   * <p>
   * Specify where to place generated source files created by annotation processing. Only applies to
   * JDK 1.6+
   * </p>
   * 
   * @since 2.2
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/annotations")
  private File generatedSourcesDirectory;

  @Override
  public Set<String> getSourceRoots() {
    return new LinkedHashSet<String>(compileSourceRoots);
  }

  @Override
  public Set<String> getIncludes() {
    return includes;
  }

  @Override
  public Set<String> getExcludes() {
    return excludes;
  }

  @Override
  public File getOutputDirectory() {
    return outputDirectory;
  }

  @Override
  public List<File> getClasspath() {
    List<File> classpath = new ArrayList<File>();
    for (Artifact artifact : compileArtifacts) {
      File file = artifact.getFile();
      if (file != null) {
        classpath.add(file);
      }
    }
    return classpath;
  }

  @Override
  public File getGeneratedSourcesDirectory() {
    return generatedSourcesDirectory;
  }
}
