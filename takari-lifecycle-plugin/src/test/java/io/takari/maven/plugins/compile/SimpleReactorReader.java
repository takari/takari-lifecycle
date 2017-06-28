package io.takari.maven.plugins.compile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenWorkspaceReader;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.util.repository.ChainedWorkspaceReader;

import com.google.common.collect.ImmutableMap;

class SimpleReactorReader implements MavenWorkspaceReader {

  private final WorkspaceRepository repository;
  private final Map<String, MavenProject> projectsByGAV;
  private final Map<String, Artifact> artifactsByGAVCE;

  private SimpleReactorReader(Collection<MavenProject> projects, Collection<Artifact> artifacts) {
    repository = new WorkspaceRepository("reactor", new Object());

    Map<String, MavenProject> projectsByGAV = new LinkedHashMap<>();
    for (MavenProject project : projects) {
      String projectKey = ArtifactUtils.key(project.getGroupId(), project.getArtifactId(), project.getVersion());
      projectsByGAV.put(projectKey, project);
    }
    this.projectsByGAV = ImmutableMap.copyOf(projectsByGAV);

    Map<String, Artifact> artifactsByGAVCE = new LinkedHashMap<>();
    for (Artifact artifact : artifacts) {
      artifactsByGAVCE.put(keyGAVCE(artifact), artifact);
    }
    this.artifactsByGAVCE = ImmutableMap.copyOf(artifactsByGAVCE);
  }

  @Override
  public WorkspaceRepository getRepository() {
    return repository;
  }

  @Override
  public File findArtifact(Artifact artifact) {
    MavenProject project = getProject(artifact);
    if (project != null) {
      if ("pom".equals(artifact.getExtension())) {
        return project.getFile();
      } else if ("jar".equals(artifact.getExtension()) && "".equals(artifact.getClassifier())) {
        return new File(project.getBuild().getOutputDirectory());
      }
    }
    Artifact _artifact = getArtifact(artifact);
    if (_artifact != null) {
      return _artifact.getFile();
    }
    return null;
  }

  private Artifact getArtifact(Artifact artifact) {
    return artifactsByGAVCE.get(keyGAVCE(artifact));
  }

  private static String keyGAVCE(Artifact artifact) {
    StringBuilder key = new StringBuilder();
    key.append(artifact.getGroupId()) //
        .append(":").append(artifact.getArtifactId()) //
        .append(":").append(artifact.getVersion()) //
        .append(":").append(artifact.getClassifier()) //
        .append(":").append(artifact.getExtension());
    return key.toString();
  }

  private MavenProject getProject(Artifact artifact) {
    String projectKey = ArtifactUtils.key(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    MavenProject project = projectsByGAV.get(projectKey);
    return project;
  }

  @Override
  public List<String> findVersions(Artifact artifact) {
    MavenProject project = getProject(artifact);
    if (project != null) {
      return Collections.singletonList(project.getVersion());
    }
    Artifact _artifact = getArtifact(artifact);
    if (_artifact != null) {
      return Collections.singletonList(_artifact.getVersion());
    }
    return null;
  }

  @Override
  public Model findModel(Artifact artifact) {
    MavenProject project = getProject(artifact);
    if (project != null) {
      return project.getModel();
    }
    return null;
  }


  //
  //
  //

  public static class Builder {

    private final List<MavenProject> projects = new ArrayList<>();
    private final List<Artifact> artifacts = new ArrayList<>();

    public SimpleReactorReader build() {
      return new SimpleReactorReader(projects, artifacts);
    }

    public Builder addProject(MavenProject project) {
      projects.add(project);
      return this;
    }

    /** <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version> */
    public Builder addArtifact(String coords, File file) {
      artifacts.add(new DefaultArtifact(coords).setFile(file));

      return this;
    }

    public void toSession(RepositorySystemSession session) {
      DefaultRepositorySystemSession _session = (DefaultRepositorySystemSession) session;
      _session.setWorkspaceReader(ChainedWorkspaceReader.newInstance(build(), _session.getWorkspaceReader()));
    }

    public Builder addProjects(MavenProject... projects) {
      for (MavenProject project : projects) {
        addProject(project);
      }

      return this;
    }

  }

  public static Builder builder() {
    return new Builder();
  }

}
