package io.takari.maven.plugins.plugin;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.takari.maven.plugins.TakariLifecycleMojo;

/**
 * Inject any plugin-specific <a href="/ref/current/maven-repository-metadata/repository-metadata.html">artifact metadata</a> to the project's artifact, for subsequent installation and deployment. It
 * is used:
 * <ol>
 * <li>to add the <code>latest</code> metadata (which is plugin-specific) for shipping alongside the plugin's artifact</li>
 * <li>to define plugin mapping in the group</li>
 * </ol>
 *
 * @see ArtifactRepositoryMetadata
 * @see GroupRepositoryMetadata
 * @version $Id: AddPluginArtifactMetadataMojo.java 1345787 2012-06-03 21:58:22Z hboutemy $
 * @since 2.0
 */
// originally copied from org.apache.maven.plugin.plugin.metadata.AddPluginArtifactMetadataMojo
@Mojo(name = "addPluginArtifactMetadata", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class AddPluginArtifactMetadataMojo extends TakariLifecycleMojo {

  /**
   * The prefix for the plugin goal.
   */
  @Parameter
  private String goalPrefix;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    Artifact projectArtifact = project.getArtifact();

    Versioning versioning = new Versioning();
    versioning.setLatest(projectArtifact.getVersion());
    versioning.updateTimestamp();
    ArtifactRepositoryMetadata metadata = new ArtifactRepositoryMetadata(projectArtifact, versioning);
    projectArtifact.addMetadata(metadata);

    GroupRepositoryMetadata groupMetadata = new GroupRepositoryMetadata(project.getGroupId());
    groupMetadata.addPluginMapping(getGoalPrefix(), project.getArtifactId(), project.getName());

    projectArtifact.addMetadata(groupMetadata);
  }

  /**
   * @return the goal prefix parameter or the goal prefix from the Plugin artifactId.
   */
  private String getGoalPrefix() {
    if (goalPrefix == null) {
      goalPrefix = PluginDescriptor.getGoalPrefixFromArtifactId(project.getArtifactId());
    }

    return goalPrefix;
  }
}
