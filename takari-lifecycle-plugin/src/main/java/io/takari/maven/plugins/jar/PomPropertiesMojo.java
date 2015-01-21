/**
 * Copyright (c) 2015 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import io.takari.incrementalbuild.BuildContext;
import io.tesla.proviso.archive.Entry;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Creates standard maven pom.properties file on filesystem.
 * <p>
 * Meant to run in place of jar mojo during m2e workspace build.
 */
@Mojo(name = "pom-properties")
public class PomPropertiesMojo extends Jar {

  @Component
  private BuildContext context;

  @Override
  public void executeMojo() throws MojoExecutionException {
    try {
      Entry entry = pomPropertiesSource(project);
      try (OutputStream os = context.processOutput(new File(classesDirectory, entry.getName())).newOutputStream()) {
        entry.writeEntry(os);
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Could not create Maven pom.properties file", e);
    }
  }

}
