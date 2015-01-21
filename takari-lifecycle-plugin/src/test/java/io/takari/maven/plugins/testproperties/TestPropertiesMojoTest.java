package io.takari.maven.plugins.testproperties;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

public class TestPropertiesMojoTest {
  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  private MojoExecution newMojoExecution() throws IOException {
    MojoExecution execution = mojos.newMojoExecution("testProperties");
    PluginDescriptor pluginDescriptor = execution.getMojoDescriptor().getPluginDescriptor();

    ArtifactHandler handler = new DefaultArtifactHandler("jar");
    DefaultArtifact workspaceResolver = new DefaultArtifact("io.takari.m2e.workspace", "org.eclipse.m2e.workspace.cli", "1", Artifact.SCOPE_COMPILE, ".jar", null, handler);
    workspaceResolver.setFile(new File("target/workspaceResolver.jar").getCanonicalFile());

    List<Artifact> pluginArtifacts = new ArrayList<>(pluginDescriptor.getArtifacts());
    pluginArtifacts.add(workspaceResolver);
    pluginDescriptor.setArtifacts(pluginArtifacts);

    return execution;
  }

  @Test
  public void testIncremental() throws Exception {
    File basedir = resources.getBasedir("testproperties/basic");
    final MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);
    session.setProjectDependencyGraph(new ProjectDependencyGraph() {
      @Override
      public List<MavenProject> getUpstreamProjects(MavenProject project, boolean transitive) {
        return Collections.emptyList();
      }

      @Override
      public List<MavenProject> getSortedProjects() {
        return Collections.singletonList(project);
      }

      @Override
      public List<MavenProject> getDownstreamProjects(MavenProject project, boolean transitive) {
        return Collections.emptyList();
      }
    });

    mojos.executeMojo(session, project, newMojoExecution());
    mojos.assertBuildOutputs(basedir, "target/test-classes/test.properties", "target/workspacestate.properties");

    File testProperties = new File(basedir, "target/test-classes/test.properties");
    File workspaceState = new File(basedir, "target/workspacestate.properties");

    long testPropertiesLastmodified = testProperties.lastModified();
    long workspaceStateLastmodified = workspaceState.lastModified();

    HashCode testPropertiesSha1 = Files.hash(testProperties, Hashing.sha1());
    HashCode workspaceStateSha1 = Files.hash(workspaceState, Hashing.sha1());

    mojos.executeMojo(session, project, newMojoExecution());
    // mojos.assertCarriedOverOutputs(basedir, "target/test-classes/test.properties", "target/workspacestate.properties");

    Assert.assertEquals(testPropertiesLastmodified, testProperties.lastModified());
    Assert.assertEquals(workspaceStateLastmodified, workspaceState.lastModified());

    Assert.assertEquals(testPropertiesSha1, sha1(basedir, "target/test-classes/test.properties"));
    Assert.assertEquals(workspaceStateSha1, sha1(basedir, "target/workspacestate.properties"));
  }

  @Test
  public void testOffline() throws Exception {
    File basedir = resources.getBasedir("testproperties/basic");

    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);

    session.getRequest().setOffline(true);
    mojos.executeMojo(session, project, newMojoExecution());
    Assert.assertEquals("true", readProperties(basedir).get("offline"));

    session.getRequest().setOffline(false);
    mojos.executeMojo(session, project, newMojoExecution());
    Assert.assertEquals("false", readProperties(basedir).get("offline"));
  }

  @Test
  public void testUpdateSnapshots() throws Exception {
    File basedir = resources.getBasedir("testproperties/basic");

    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);

    session.getRequest().setUpdateSnapshots(true);
    mojos.executeMojo(session, project, newMojoExecution());
    Assert.assertEquals("true", readProperties(basedir).get("updateSnapshots"));

    session.getRequest().setUpdateSnapshots(false);
    mojos.executeMojo(session, project, newMojoExecution());
    Assert.assertEquals("false", readProperties(basedir).get("updateSnapshots"));
  }

  @Test
  public void testCustomTestPropertiesFile() throws Exception {
    File basedir = resources.getBasedir("testproperties/custom-test-properties-file");
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);

    mojos.executeMojo(session, project, newMojoExecution());
    Assert.assertEquals("value", readProperties(basedir).get("custom"));

    TestResources.cp(basedir, "src/test/modified-test.properties", "src/test/test.properties");
    mojos.executeMojo(session, project, newMojoExecution());
    Assert.assertEquals("modified-value", readProperties(basedir).get("custom"));
  }

  private HashCode sha1(File basedir, String path) throws IOException {
    return Files.hash(new File(basedir, path), Hashing.sha1());
  }

  private Map<String, String> readProperties(File basedir) throws IOException {
    Properties properties = new Properties();
    try (InputStream is = new FileInputStream(new File(basedir, "target/test-classes/test.properties"))) {
      properties.load(is);
    }
    Map<String, String> result = new HashMap<>();
    for (String key : properties.stringPropertyNames()) {
      result.put(key, properties.getProperty(key));
    }
    return result;
  }
}
