package io.tesla.maven.plugins.compilerXXX;

import io.takari.incrementalbuild.configuration.Configuration;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "testCompileXXX", defaultPhase = LifecyclePhase.TEST_COMPILE, requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST)
public class TestCompileMojo extends AbstractCompileMojo {
  /**
   * The source directories containing the test-source to be compiled.
   */
  @Parameter(defaultValue = "${project.testCompileSourceRoots}", readonly = true, required = true)
  private List<String> testCompileSourceRoots;

  /**
   * Project test classpath.
   */
  // note that dependency changes are handled incrementally, hence @Configuration(ignored=true)
  @Parameter(defaultValue = "${project.testClasspathElements}", required = true, readonly = true)
  @Configuration(ignored = true)
  private List<String> testClasspathElements;

  @Parameter(defaultValue = "${project.testArtifacts}", readonly = true, required = true)
  @Configuration(ignored = true)
  private List<Artifact> testCompileArtifacts;

  /**
   * The directory where compiled test classes go.
   */
  @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true, readonly = true)
  private File testOutputDirectory;

  /**
   * A list of inclusion filters for the compiler.
   */
  @Parameter
  private Set<String> testIncludes = new HashSet<String>();

  /**
   * A list of exclusion filters for the compiler.
   */
  @Parameter
  private Set<String> testExcludes = new HashSet<String>();

  @Override
  public List<String> getClasspathElements() {
    return testClasspathElements;
  }

  @Override
  public List<Artifact> getCompileArtifacts() {
    return testCompileArtifacts;
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
