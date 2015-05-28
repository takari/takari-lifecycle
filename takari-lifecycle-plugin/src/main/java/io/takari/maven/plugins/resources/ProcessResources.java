/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.resources;

import java.io.File;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "process-resources", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, configurator = "takari", threadSafe = true)
public class ProcessResources extends AbstractProcessResourcesMojo {

  @Parameter(defaultValue = "${project.build.outputDirectory}", property = "resources.outputDirectory")
  private File outputDirectory;

  @Parameter
  private List<Resource> resources;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    process(resources != null ? resources : project.getBuild().getResources(), outputDirectory);
  }

}
