package io.tesla.maven.plugins.compiler;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sonatype.maven.plugin.ClasspathType;
import org.sonatype.maven.plugin.Conf;
import org.sonatype.maven.plugin.LifecycleGoal;
import org.sonatype.maven.plugin.LifecyclePhase;
import org.sonatype.maven.plugin.RequiresDependencyResolution;

@LifecycleGoal(goal = "compile", phase = LifecyclePhase.COMPILE, requireProject = true)
@RequiresDependencyResolution(ClasspathType.COMPILE)
public class CompileMojo extends AbstractCompileMojo {
  /**
   * The source directories containing the sources to be compiled.
   */
  @Conf(defaultValue = "${project.compileSourceRoots}", readOnly = true, required = true)
  private List<String> compileSourceRoots;

  /**
   * A list of inclusion filters for the compiler.
   */
  @Conf
  private Set<String> includes = new HashSet<String>();

  /**
   * A list of exclusion filters for the compiler.
   */
  @Conf
  private Set<String> excludes = new HashSet<String>();

  /**
   * Project classpath.
   */
  @Conf(defaultValue = "${project.compileClasspathElements}", readOnly = true, required = true)
  private List<String> classpathElements;

  /**
   * The directory for compiled classes.
   */
  @Conf(defaultValue = "${project.build.outputDirectory}", required = true, readOnly = true)
  private File outputDirectory;

  @Override
  public List<String> getClasspathElements() {
    return classpathElements;
  }

  @Override
  public File getOutputDirectory() {
    return outputDirectory;
  }

  @Override
  public Set<String> getSourceExcludes() {
    return excludes;
  }

  @Override
  public Set<String> getSourceIncludes() {
    return includes;
  }

  @Override
  public List<String> getSourceRoots() {
    return compileSourceRoots;
  }
}
