/**
 * Copyright (c) 2015 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import io.takari.maven.plugins.util.PropertiesWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.maven.project.MavenProject;

class PomProperties {
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
