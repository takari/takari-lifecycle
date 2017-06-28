/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.testproperties;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.m2e.workspace.MutableWorkspaceState;

import com.google.common.collect.ImmutableSet;

import io.takari.incrementalbuild.BasicBuildContext;
import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.maven.plugins.util.PropertiesWriter;
import io.takari.resources.filtering.ResourcesProcessor;

@Mojo(name = "testProperties", requiresDependencyResolution = ResolutionScope.TEST, configurator = "takari", threadSafe = true)
public class TestPropertiesMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.properties}", readonly = true)
  private Properties projectProperties;

  @Parameter(defaultValue = "${session.executionProperties}", readonly = true)
  private Properties sessionProperties;

  @Parameter(defaultValue = "${project}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  protected MavenProject project;

  @Parameter(defaultValue = "${project.groupId}", readonly = true)
  private String groupId;

  @Parameter(defaultValue = "${project.artifactId}", readonly = true)
  private String artifactId;

  @Parameter(defaultValue = "${project.version}", readonly = true)
  private String version;

  @Parameter(defaultValue = "${localRepository}", readonly = true)
  private ArtifactRepository localRepository;

  @Parameter(defaultValue = "${session.request.userSettingsFile}", readonly = true)
  private File userSettingsFile;

  @Parameter(defaultValue = "${session.request.globalSettingsFile}", readonly = true)
  private File globalSettingsFile;

  @Parameter(defaultValue = "${project.basedir}/src/test/test.properties")
  private File testProperties;

  @Parameter(defaultValue = "${project.build.testOutputDirectory}/test.properties")
  private File outputFile;

  // @Parameter(defaultValue = "${plugin.artifactMap(io.takari.m2e.workspace:org.eclipse.m2e.workspace.cli)}")
  @Parameter(defaultValue = "${project.artifactMap(io.takari.m2e.workspace:org.eclipse.m2e.workspace.cli)}", readonly = true)
  private Artifact workspaceResolver;

  @Parameter(defaultValue = "${project.build.directory}/workspacestate.properties")
  private File workspaceState;

  @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
  private File outputDirectory;

  @Parameter(defaultValue = "${project.artifacts}", readonly = true)
  private Set<Artifact> dependencies;

  @Parameter(defaultValue = "${session.request.offline}", readonly = true)
  private boolean offline;

  @Parameter(defaultValue = "${session.request.updateSnapshots}", readonly = true)
  private boolean updateSnapshots;

  @Parameter(defaultValue = "${session.projectDependencyGraph}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  private ProjectDependencyGraph reactorDependencies;

  @Component
  private BasicBuildContext context;

  @Component
  private ResourcesProcessor resourceProcessor;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      Properties properties = new Properties();

      if (testProperties.canRead()) {
        mergeCustomTestProperties(properties);
      }

      if (!context.isProcessingRequired()) {
        return;
      }

      // well-known properties, TODO introduce named constants
      putIfAbsent(properties, "localRepository", localRepository.getBasedir());
      if (isAccessible(userSettingsFile)) {
        putIfAbsent(properties, "userSettingsFile", userSettingsFile.getAbsolutePath());
      } else {
        logWarningNotAccessibleFile(userSettingsFile);
      }
      if (isAccessible(globalSettingsFile)) {
        putIfAbsent(properties, "globalSettingsFile", globalSettingsFile.getAbsolutePath());
      } else {
        logWarningNotAccessibleFile(globalSettingsFile);
      }
      List<ArtifactRepository> repositories = project.getRemoteArtifactRepositories();
      for (int i = 0; i < repositories.size(); i++) {
        properties.put("repository." + i, toString(repositories.get(i)));
      }
      putIfAbsent(properties, "offline", Boolean.toString(offline));
      putIfAbsent(properties, "updateSnapshots", Boolean.toString(updateSnapshots));
      putIfAbsent(properties, "project.groupId", groupId);
      putIfAbsent(properties, "project.artifactId", artifactId);
      putIfAbsent(properties, "project.version", version);

      // project runtime classpath
      putIfAbsent(properties, "classpath", getClasspathString());

      if (workspaceResolver != null && workspaceResolver.getFile() != null) {
        putIfAbsent(properties, "workspaceResolver", workspaceResolver.getFile().getAbsolutePath());
      }

      writeWorkspaceState();
      putIfAbsent(properties, "workspaceStateProperties", workspaceState.getAbsolutePath());

      try (OutputStream os = context.processOutput(outputFile).newOutputStream()) {
        PropertiesWriter.write(properties, "Generated by " + getClass().getName(), os);
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Could not create test.properties file", e);
    }
  }

  private String toString(ArtifactRepository repository) {
    StringBuilder sb = new StringBuilder();
    sb.append("<id>").append(repository.getId()).append("</id>");
    sb.append("<url>").append(repository.getUrl()).append("</url>");
    sb.append("<releases><enabled>").append(repository.getReleases().isEnabled()).append("</enabled></releases>");
    sb.append("<snapshots><enabled>").append(repository.getSnapshots().isEnabled()).append("</enabled></snapshots>");
    return sb.toString();
  }

  private String getClasspathString() {
    Set<String> scopes = ImmutableSet.of(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME);
    StringBuilder sb = new StringBuilder();
    sb.append(outputDirectory.getAbsolutePath());
    for (Artifact dependency : dependencies) {
      if (scopes.contains(dependency.getScope())) {
        sb.append(File.pathSeparatorChar);
        sb.append(dependency.getFile().getAbsolutePath());
      }
    }
    return sb.toString();
  }

  private void writeWorkspaceState() throws MojoExecutionException {
    MutableWorkspaceState state = new MutableWorkspaceState();
    // always include this project's pom and jar artifacts
    state.putPom(project.getFile(), project.getGroupId(), project.getArtifactId(), project.getVersion());
    state.putArtifact(outputDirectory, project.getGroupId(), project.getArtifactId(), //
        "jar" /* extension */, null/* classifier */, project.getVersion());
    if (reactorDependencies != null) {
      // either runs from m2e or from command line with --non-recursive parameter
      for (MavenProject other : reactorDependencies.getUpstreamProjects(project, true)) {
        putProject(state, other);
      }
    }
    try (OutputStream os = context.processOutput(workspaceState).newOutputStream()) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      state.store(buffer);
      PropertiesWriter.write(buffer.toByteArray(), os);
    } catch (IOException e) {
      throw new MojoExecutionException("Could not create reactor state file " + workspaceState, e);
    }
  }

  private static void putProject(MutableWorkspaceState state, MavenProject other) {
    state.putPom(other.getFile(), other.getGroupId(), other.getArtifactId(), other.getVersion());
    if (other.getArtifact().getFile() != null) {
      putArtifact(state, other.getArtifact());
    }
    for (Artifact artifact : other.getAttachedArtifacts()) {
      putArtifact(state, artifact);
    }
  }

  private static void putArtifact(MutableWorkspaceState state, Artifact artifact) {
    state.putArtifact(artifact.getFile(), artifact.getGroupId(), artifact.getArtifactId(), //
        artifact.getArtifactHandler().getExtension(), artifact.getClassifier(), artifact.getBaseVersion());
  }

  private void mergeCustomTestProperties(Properties properties) throws MojoExecutionException {
    ResourceMetadata<File> metadata = context.registerInput(testProperties);
    try (InputStream is = new FileInputStream(metadata.getResource())) {
      Properties custom = new Properties();
      custom.load(is);
      mergeCustomTestProperties(properties, custom);
    } catch (IOException e) {
      // TODO create error marker instead
      throw new MojoExecutionException("Could not read test.properties file " + testProperties, e);
    }
  }

  private void mergeCustomTestProperties(Properties properties, Properties custom) {
    // resource filtering configuration should match AbstractProcessResourcesMojo
    // TODO figure out how to move this to a common component
    Map<Object, Object> substitutes = new HashMap<Object, Object>();

    for (Artifact dependency : dependencies) {
      StringBuilder key = new StringBuilder();
      key.append(dependency.getGroupId());
      key.append(':').append(dependency.getArtifactId());
      if (dependency.getClassifier() != null) {
        key.append(':').append(dependency.getClassifier());
      }
      substitutes.put(key.toString(), dependency.getFile());
    }

    substitutes.putAll(projectProperties);
    substitutes.putAll(sessionProperties);
    substitutes.put("project", project);
    substitutes.put("localRepository", localRepository);
    substitutes.put("userSettingsFile", userSettingsFile);

    for (String key : custom.stringPropertyNames()) {
      properties.put(key, expand(custom.getProperty(key), substitutes));
    }
  }

  private static void putIfAbsent(Properties properties, String key, String value) {
    if (!properties.containsKey(key)) {
      properties.put(key, value);
    }
  }

  private String expand(String value, Map<Object, Object> substitutes) {
    StringWriter writer = new StringWriter();
    try {
      resourceProcessor.filter(new StringReader(value), writer, substitutes);
      return writer.toString();
    } catch (IOException e) {
      return value; // shouldn't happen
    }
  }

  private boolean isAccessible(File file) {
    return file != null && file.isFile() && file.canRead();
  }

  private void logWarningNotAccessibleFile(File file) {
    if (file != null) {
      String msg = "File '" + file.getAbsolutePath() + "' ";
      if (file.exists() && !file.isFile()) {
        msg += "exists, but it is not a regular file!";
      } else if (file.exists() && file.isFile() && !file.canRead()) {
        msg += "exists, but can not be read!";
      } else {
        msg += "does not exist!";
      }
      msg += " It will be ignored.";
      getLog().warn(msg);
    }
  }
}
