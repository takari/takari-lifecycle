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
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.AbstractRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Plugin;

/**
 * Metadata for the group directory of the repository.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
// originally copied from org.apache.maven.artifact.repository.metadata.GroupRepositoryMetadata
class GroupRepositoryMetadata extends AbstractRepositoryMetadata {
  private final String groupId;

  public GroupRepositoryMetadata(String groupId) {
    super(new Metadata());
    this.groupId = groupId;
  }

  @Override
  public boolean storedInGroupDirectory() {
    return true;
  }

  @Override
  public boolean storedInArtifactVersionDirectory() {
    return false;
  }

  @Override
  public String getGroupId() {
    return groupId;
  }

  @Override
  public String getArtifactId() {
    return null;
  }

  @Override
  public String getBaseVersion() {
    return null;
  }

  public void addPluginMapping(String goalPrefix, String artifactId) {
    addPluginMapping(goalPrefix, artifactId, artifactId);
  }

  public void addPluginMapping(String goalPrefix, String artifactId, String name) {
    List plugins = getMetadata().getPlugins();
    boolean found = false;
    for (Iterator i = plugins.iterator(); i.hasNext() && !found;) {
      Plugin plugin = (Plugin) i.next();
      if (plugin.getPrefix().equals(goalPrefix)) {
        found = true;
      }
    }
    if (!found) {
      Plugin plugin = new Plugin();
      plugin.setPrefix(goalPrefix);
      plugin.setArtifactId(artifactId);
      plugin.setName(name);


      getMetadata().addPlugin(plugin);
    }
  }

  @Override
  public Object getKey() {
    return groupId;
  }

  @Override
  public boolean isSnapshot() {
    return false;
  }

  @Override
  public ArtifactRepository getRepository() {
    return null;
  }

  @Override
  public void setRepository(ArtifactRepository remoteRepository) {
    // intentionally blank
  }
}
