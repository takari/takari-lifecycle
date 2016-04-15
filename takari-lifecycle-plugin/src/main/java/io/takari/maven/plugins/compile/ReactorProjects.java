package io.takari.maven.plugins.compile;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.SessionScoped;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import com.google.common.collect.ImmutableMap;

@Named
@SessionScoped
class ReactorProjects {

  private final Map<String, MavenProject> projects;

  @Inject
  public ReactorProjects(MavenSession session) {
    Map<String, MavenProject> projects = new HashMap<>();
    for (MavenProject project : session.getProjects()) {
      projects.put(key(project.getGroupId(), project.getArtifactId(), project.getVersion()), project);
    }
    this.projects = ImmutableMap.copyOf(projects);
  }

  public MavenProject get(Artifact artifact) {
    return projects.get(key(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
  }

  private static String key(String groupId, String artifactId, String version) {
    return groupId + ":" + artifactId + ":" + version;
  }
}
