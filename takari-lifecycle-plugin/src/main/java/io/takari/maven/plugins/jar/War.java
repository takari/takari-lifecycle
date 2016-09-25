/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.aether.artifact.Artifact;

import com.google.common.io.Files;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;

@Mojo(name = "war", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME, configurator = "takari")
public class War extends Jar {

  @Parameter(defaultValue = "${project.artifacts}", readonly = true, required = true)
  @Incremental(configuration = Configuration.ignore)
  private Set<Artifact> artifacts;

  private void provisionWar() throws IOException {
    logger.info("Runtime artifacts: project.getArtifacts(): " + project.getArtifacts());
    logger.info("Runtime artifacts ${project.artifacts}: " + artifacts.toString());
    for (Artifact artifact : artifacts) {
      File source = artifact.getFile();
      File target = new File(outputDirectory, source.getName());
      logger.info(String.format("copying %s to %s", source, target));
      Files.copy(source, target);
    }
  }


}
