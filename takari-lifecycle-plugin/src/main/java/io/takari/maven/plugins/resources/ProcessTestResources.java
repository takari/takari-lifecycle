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

@Mojo(name = "process-test-resources", defaultPhase = LifecyclePhase.PROCESS_TEST_RESOURCES, configurator = "takari", threadSafe = true)
public class ProcessTestResources extends AbstractProcessResourcesMojo {

  @Parameter(defaultValue = "${project.build.testOutputDirectory}", property = "resources.testOutputDirectory")
  private File testOutputDirectory;

  @Parameter
  private List<Resource> testResources;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    process(testResources != null ? testResources : project.getBuild().getTestResources(), testOutputDirectory);
  }
}
