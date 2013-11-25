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

@LifecycleGoal(goal = "testCompile", phase = LifecyclePhase.TEST_COMPILE, requireProject = true)
@RequiresDependencyResolution(ClasspathType.TEST)
public class TestCompileMojo extends AbstractCompileMojo {
  /**
   * The source directories containing the test-source to be compiled.
   */
  @Conf(defaultValue = "${project.testCompileSourceRoots}", readOnly = true, required = true)
  private List<String> testCompileSourceRoots;

  /**
   * Project test classpath.
   */
  @Conf(defaultValue = "${project.testClasspathElements}", required = true, readOnly = true)
  private List<String> testClasspathElements;

  /**
   * The directory where compiled test classes go.
   */
  @Conf(defaultValue = "${project.build.testOutputDirectory}", required = true, readOnly = true)
  private File testOutputDirectory;

  /**
   * A list of inclusion filters for the compiler.
   */
  @Conf
  private Set<String> testIncludes = new HashSet<String>();

  /**
   * A list of exclusion filters for the compiler.
   */
  @Conf
  private Set<String> testExcludes = new HashSet<String>();

  @Override
  public List<String> getClasspathElements() {
    return testClasspathElements;
  }

  @Override
  public File getOutputDirectory() {
    return testOutputDirectory;
  }

  @Override
  public Set<String> getSourceExcludes() {
    return testExcludes;
  }

  @Override
  public Set<String> getSourceIncludes() {
    return testIncludes;
  }

  @Override
  public List<String> getSourceRoots() {
    return testCompileSourceRoots;
  }
}
