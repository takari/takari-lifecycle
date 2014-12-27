package io.takari.maven.plugins.testproperties;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.base.Throwables;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

public class TestPropertiesMojoTest {
  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule() {
    @Override
    public MavenSession newMavenSession(final MavenProject project) {
      MavenExecutionRequest request = new DefaultMavenExecutionRequest();
      try {
        lookup(MavenExecutionRequestPopulator.class).populateDefaults(request);
      } catch (Exception e) {
        throw Throwables.propagate(e);
      }
      MavenExecutionResult result = new DefaultMavenExecutionResult();
      MavenSession session = new MavenSession(getContainer(), MavenRepositorySystemUtils.newSession(), request, result);
      session.setCurrentProject(project);
      session.setProjects(Arrays.asList(project));
      session.setProjectDependencyGraph(new ProjectDependencyGraph() {

        @Override
        public List<MavenProject> getSortedProjects() {
          return Collections.singletonList(project);
        }

        @Override
        public List<MavenProject> getDownstreamProjects(MavenProject project, boolean transitive) {
          return Collections.emptyList();
        }

        @Override
        public List<MavenProject> getUpstreamProjects(MavenProject project, boolean transitive) {
          return Collections.emptyList();
        }
      });
      return session;
    }

    @Override
    public Mojo lookupConfiguredMojo(MavenSession session, MojoExecution execution) throws Exception {
      Mojo mojo = super.lookupConfiguredMojo(session, execution);
      ArtifactHandler handler = new DefaultArtifactHandler("jar");
      Artifact workspaceResolver = new DefaultArtifact("g", "a", "v", "s", "t", "c", handler);
      workspaceResolver.setFile(new File("target/workspaceResolver.jar").getCanonicalFile());
      ReflectionUtils.setVariableValueInObject(mojo, "workspaceResolver", workspaceResolver);
      return mojo;
    }
  };

  @Test
  public void testIncremental() throws Exception {
    File basedir = resources.getBasedir("testproperties");
    mojos.executeMojo(basedir, "testProperties");
    mojos.assertBuildOutputs(basedir, "target/test-classes/test.properties", "target/workspacestate.properties");

    File testProperties = new File(basedir, "target/test-classes/test.properties");
    File workspaceState = new File(basedir, "target/workspacestate.properties");

    long testPropertiesLastmodified = testProperties.lastModified();
    long workspaceStateLastmodified = workspaceState.lastModified();

    HashCode testPropertiesSha1 = Files.hash(testProperties, Hashing.sha1());
    HashCode workspaceStateSha1 = Files.hash(workspaceState, Hashing.sha1());

    mojos.executeMojo(basedir, "testProperties");
    // mojos.assertCarriedOverOutputs(basedir, "target/test-classes/test.properties", "target/workspacestate.properties");

    Assert.assertEquals(testPropertiesLastmodified, testProperties.lastModified());
    Assert.assertEquals(workspaceStateLastmodified, workspaceState.lastModified());

    Assert.assertEquals(testPropertiesSha1, sha1(basedir, "target/test-classes/test.properties"));
    Assert.assertEquals(workspaceStateSha1, sha1(basedir, "target/workspacestate.properties"));
  }

  private HashCode sha1(File basedir, String path) throws IOException {
    return Files.hash(new File(basedir, path), Hashing.sha1());
  }
}
