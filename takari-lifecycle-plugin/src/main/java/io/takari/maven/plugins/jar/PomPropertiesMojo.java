/**
 * Copyright (c) 2015 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;
import io.takari.maven.plugins.util.PropertiesWriter;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Creates standard maven pom.properties file on filesystem. Meant for m2e integration as jar mojo creates corresponding archive entry directly.
 */
@Mojo(name = "pom-properties")
public class PomPropertiesMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  @Incremental(configuration = Configuration.ignore)
  private MavenProject project;

  @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
  private File outputDirectory;

  @Component
  private BuildContext context;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try (OutputStream os = context.processOutput(new File(outputDirectory, entryPath(project))).newOutputStream()) {
      writeTo(project, os);
    } catch (IOException e) {
      throw new MojoExecutionException("Could not create Maven pom.properties file", e);
    }
  }

  public static String entryPath(MavenProject project) {
    return String.format("META-INF/maven/%s/%s/pom.properties", project.getGroupId(), project.getArtifactId());
  }

  public static void writeTo(MavenProject project, OutputStream out) throws IOException {
    Properties properties = new Properties();
    properties.setProperty("groupId", project.getGroupId());
    properties.setProperty("artifactId", project.getArtifactId());
    properties.setProperty("version", project.getVersion());

    PropertiesWriter.write(properties, null, out);
  }

}
