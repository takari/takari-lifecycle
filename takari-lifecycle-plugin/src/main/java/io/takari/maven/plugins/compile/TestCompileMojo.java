/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;

@Mojo(name = "testCompile", defaultPhase = LifecyclePhase.TEST_COMPILE, threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST, configurator = "takari")
public class TestCompileMojo extends AbstractCompileMojo {

  /**
   * The source directories containing the test-source to be compiled.
   */
  @Parameter(defaultValue = "${project.testCompileSourceRoots}", readonly = true, required = true)
  private List<String> compileSourceRoots;

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

  /**
   * Project classpath.
   */
  @Parameter(defaultValue = "${project.testArtifacts}", readonly = true, required = true)
  @Incremental(configuration = Configuration.ignore)
  private List<Artifact> compileArtifacts;

  /**
   * Project main output directory, part of test classpath.
   */
  @Parameter(defaultValue = "${project.build.outputDirectory}", required = true, readonly = true)
  private File mainOutputDirectory;

  /**
   * The directory where compiled test classes go.
   */
  @Parameter(defaultValue = "${project.build.testOutputDirectory}", required = true, readonly = true)
  private File outputDirectory;

  /**
   * <p>
   * Specify where to place generated source files created by annotation processing. Only applies to JDK 1.6+
   * </p>
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/test-annotations")
  private File generatedTestSourcesDirectory;

  /**
   * Set this to 'true' to bypass compilation of test sources. Its use is NOT RECOMMENDED, but quite convenient on occasion.
   */
  @Parameter(property = "maven.test.skip")
  @Incremental(configuration = Configuration.ignore)
  private boolean skip;

  /**
   * Main compile source roots, part of test sourcepath.
   */
  @Parameter(defaultValue = "${project.compileSourceRoots}", readonly = true, required = true)
  private List<String> mainCompileSourceRoots;

  @Override
  public Set<String> getSourceRoots() {
    return new LinkedHashSet<String>(compileSourceRoots);
  }

  @Override
  public Set<String> getIncludes() {
    return testIncludes;
  }

  @Override
  public Set<String> getExcludes() {
    return testExcludes;
  }

  @Override
  public File getOutputDirectory() {
    return outputDirectory;
  }

  @Override
  public List<Artifact> getClasspathArtifacts() {
    return compileArtifacts;
  }

  @Override
  public File getGeneratedSourcesDirectory() {
    return generatedTestSourcesDirectory;
  }

  @Override
  protected boolean isSkip() {
    return skip;
  }

  @Override
  protected File getMainOutputDirectory() {
    return mainOutputDirectory;
  }

  @Override
  protected void addGeneratedSources(MavenProject project) {
    List<String> roots = project.getTestCompileSourceRoots();
    String root = generatedTestSourcesDirectory.getAbsolutePath();
    if (!roots.contains(root)) {
      roots.add(root);
    }
  }

  @Override
  protected Set<String> getMainSourceRoots() {
    return new LinkedHashSet<>(mainCompileSourceRoots);
  }

  @Override
  protected List<Dependency> getProcessorpathDependencies() {
    return null;
  }
}
