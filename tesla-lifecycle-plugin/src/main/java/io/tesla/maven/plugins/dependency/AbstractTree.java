package io.tesla.maven.plugins.dependency;

import io.tesla.maven.plugins.dependency.tree.serializer.TreeRenderer;

import java.util.List;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.sonatype.maven.plugin.Conf;

public abstract class AbstractTree extends AbstractMojo {

  @Inject
  private RepositorySystem repositorySystem;

  @Conf( defaultValue = "${project}", readOnly = true)
  private MavenProject project;

  @Conf( defaultValue = "${reactorProjects}", readOnly = true)
  private List<MavenProject> reactorProjects;

  @Conf( defaultValue = "${repositorySystemSession}", readOnly = true)
  private DefaultRepositorySystemSession repositorySystemSession;

  @Conf( defaultValue = "${project.remoteProjectRepositories}", readOnly = true)
  private List<RemoteRepository> remoteRepos;

  public void execute() throws MojoExecutionException, MojoFailureException {

    MavenProject lastProject = reactorProjects.get(reactorProjects.size() - 1);
    if (lastProject.equals(project)) {
      if(reactorProjects.size() > 1) {
        getLog().info("");
        getLog().info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        getLog().info("");
        getLog().info("You can't run this from an aggregator. Step into a project.");
        getLog().info("");
        getLog().info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        getLog().info("");
        return;
      }
      
      if(project.getPackaging().equals("pom")) {
        getLog().info("");
        getLog().info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        getLog().info("");
        getLog().info("You can't run this from a parent pom. Step into a project.");
        getLog().info("");
        getLog().info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        getLog().info("");
        return;
      }       
    } else {
      return;
    }
    
    DefaultRepositorySystemSession repositorySystemSessionForTree = new DefaultRepositorySystemSession(repositorySystemSession);
    repositorySystemSessionForTree.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
    repositorySystemSessionForTree.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
    Artifact artifact = mavenToAetherArtifact(project.getArtifact());

    try {
      ArtifactDescriptorResult descriptorResult = repositorySystem.readArtifactDescriptor(repositorySystemSessionForTree, 
          new ArtifactDescriptorRequest(artifact, remoteRepos, ""));

      CollectRequest collectRequest = new CollectRequest();      
      collectRequest.setRootArtifact(descriptorResult.getArtifact());
      collectRequest.setDependencies(descriptorResult.getDependencies());
      collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());
      for (RemoteRepository repo : remoteRepos) {
        collectRequest.addRepository(repo);
      }
            
      CollectResult collectResult = repositorySystem.collectDependencies(repositorySystemSessionForTree, collectRequest);
      renderer().render(collectResult.getRoot());
      //
      // dot -T pdf | open -a /Applications/Preview.app -f
      //
    } catch (DependencyCollectionException e) {
      throw new MojoExecutionException("Failed to display tree for project.", e);
    } catch (ArtifactDescriptorException e) {
      throw new MojoExecutionException("Failed to display tree for project.", e);
    }
  }

  protected abstract TreeRenderer renderer();

  //
  // <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
  //
  private Artifact mavenToAetherArtifact(org.apache.maven.artifact.Artifact ma) {
    if (ma.getType() != null && ma.getClassifier() != null) {
      //
      // <groupId>:<artifactId>:<extension>:<classifier>:<version>
      //
      return new DefaultArtifact(String.format("%s:%s:%s:%s:%s", ma.getGroupId(), ma.getArtifactId(), ma.getType(), ma.getClassifier(), ma.getVersion()));
    } else if (ma.getType() != null) {
      //
      // <groupId>:<artifactId>:<extension>:<version>
      //
      return new DefaultArtifact(String.format("%s:%s:%s:%s", ma.getGroupId(), ma.getArtifactId(), ma.getType(), ma.getVersion()));
    } else {
      //
      // <groupId>:<artifactId>:<version>
      //
      return new DefaultArtifact(String.format("%s:%s:%s", ma.getGroupId(), ma.getArtifactId(), ma.getVersion()));
    }
  }
}
