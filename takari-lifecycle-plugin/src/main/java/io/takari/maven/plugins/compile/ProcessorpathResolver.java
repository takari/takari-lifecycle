/**
 * Copyright (c) 2017 Salesforce.com, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.filter.AndDependencyFilter;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;

import com.google.common.base.Strings;

@Named
class ProcessorpathResolver {

  private final RepositorySystem aether;

  @Inject
  public ProcessorpathResolver(RepositorySystem aether) {
    this.aether = aether;
  }

  public List<File> resolve(RepositorySystemSession session, MavenProject project, List<Dependency> dependencies) throws MojoExecutionException {
    // copy&paste from org.apache.maven.project.DefaultProjectDependenciesResolver.resolve(DependencyResolutionRequest)
    // aether is sad, really sad

    ArtifactTypeRegistry stereotypes = session.getArtifactTypeRegistry();

    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setRootArtifact(RepositoryUtils.toArtifact(project.getArtifact()));
    collectRequest.setRequestContext("project");
    collectRequest.setRepositories(project.getRemoteProjectRepositories());

    Map<String, Artifact> artifacts = project.getArtifactMap();
    for (Dependency dependency : dependencies) {
      if (Strings.isNullOrEmpty(dependency.getVersion())) {
        Artifact artifact = artifacts.get(ArtifactUtils.versionlessKey(dependency.getGroupId(), dependency.getArtifactId()));
        if (artifact != null) {
          dependency = dependency.clone();
          dependency.setVersion(artifact.getVersion());
        }
      }
      collectRequest.addDependency(RepositoryUtils.toDependency(dependency, stereotypes));
    }

    DependencyManagement depMngt = project.getDependencyManagement();
    if (depMngt != null) {
      for (Dependency dependency : depMngt.getDependencies()) {
        collectRequest.addManagedDependency(RepositoryUtils.toDependency(dependency, stereotypes));
      }
    }

    DependencyFilter collectionFilter = new ScopeDependencyFilter(null, Collections.singleton("test"));
    DependencyFilter resolutionFilter = new ScopeDependencyFilter(null, Collections.singleton("test"));
    resolutionFilter = AndDependencyFilter.newInstance(collectionFilter, resolutionFilter);

    DependencyRequest depRequest = new DependencyRequest(collectRequest, resolutionFilter);

    try {
      DependencyNode node = aether.collectDependencies(session, collectRequest).getRoot();
      depRequest.setRoot(node);
      List<File> processorpath = new ArrayList<>();
      List<Exception> errors = new ArrayList<>();
      for (ArtifactResult artifactResult : aether.resolveDependencies(session, depRequest).getArtifactResults()) {
        if (artifactResult.isResolved()) {
          processorpath.add(artifactResult.getArtifact().getFile());
        } else {
          errors.addAll(artifactResult.getExceptions());
        }
      }

      if (!errors.isEmpty()) {
        StringBuilder msg = new StringBuilder();
        errors.forEach(e -> msg.append(e.getMessage()).append(", "));
        throw new MojoExecutionException("Could not resolve processorpath: ");
      }

      return processorpath;
    } catch (DependencyCollectionException | DependencyResolutionException e) {
      throw new MojoExecutionException("Could not resolve processorpath: " + e.getMessage(), e);
    }
  }

}
