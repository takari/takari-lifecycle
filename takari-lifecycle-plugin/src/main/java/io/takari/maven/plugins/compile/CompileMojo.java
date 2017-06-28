/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile;

import java.io.File;
import java.util.Collections;
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

@Mojo(name = "compile", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE, configurator = "takari")
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
   * Specify where to place generated source files created by annotation processing. Only applies to JDK 1.6+
   * </p>
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/annotations")
  private File generatedSourcesDirectory;

  /**
   * Set this to 'true' to bypass compilation of main sources. Its use is NOT RECOMMENDED, but quite convenient on occasion.
   */
  @Parameter(property = "maven.main.skip")
  @Incremental(configuration = Configuration.ignore)
  private boolean skipMain;

  /**
   * Processor path defines where to find annotation processors; if not configured, project classpath will be searched for processors. The primary usecase is to avoid unintentional "leakage" of
   * build-time tools and their dependencies to production classpath. Additionally, separate processor path is likely to reduce number of incremental builds escalated due to annotation processing
   * runtime path change.
   * 
   * <p/>
   * Configuration uses the same syntax as project {@code <dependency>}, and processor path is resolved using {@code compile+runtime} resolution scope.
   * 
   * <p/>
   * To help align artifacts versions used by project classpath path and processor path:
   * <ul>
   * <li>Project {@code <dependencyManagement>} is used during processor path resolution.</li>
   * <li>If processor path dependency {@code <version>} is omitted and project dependencies have artifact with matching (groupId,artifactId) tuple, the project dependency version will be used to
   * resolve processor path.</li>
   * </ul>
   * 
   * <strong>EXPERIMENTAL</strong>. This parameter is experimental and can be changed or removed without prior notice.
   * 
   * @since 1.12.6
   */
  @Parameter
  @Incremental(configuration = Configuration.ignore)
  private List<Dependency> processorpath;

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
  public List<Artifact> getClasspathArtifacts() {
    return compileArtifacts;
  }

  @Override
  public File getGeneratedSourcesDirectory() {
    return generatedSourcesDirectory;
  }

  @Override
  protected boolean isSkip() {
    return skipMain;
  }

  @Override
  protected File getMainOutputDirectory() {
    return null; // main compile does not have corresponding main classes directory
  }

  @Override
  protected void addGeneratedSources(MavenProject project) {
    List<String> roots = project.getCompileSourceRoots();
    String root = generatedSourcesDirectory.getAbsolutePath();
    if (!roots.contains(root)) {
      roots.add(root);
    }
  }

  @Override
  protected Set<String> getMainSourceRoots() {
    return Collections.emptySet(); // main compile does not have corresponding main sources
  }

  @Override
  protected List<Dependency> getProcessorpathDependencies() {
    return processorpath;
  }
}
