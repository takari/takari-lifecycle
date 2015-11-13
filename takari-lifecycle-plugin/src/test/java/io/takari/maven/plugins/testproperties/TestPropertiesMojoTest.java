package io.takari.maven.plugins.testproperties;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

public class TestPropertiesMojoTest {
  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

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

  @Test
  public void testWorkspaceStateIncludesThisProjectJarArtifact() throws Exception {
    File basedir = resources.getBasedir();
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);

    mojos.executeMojo(session, project, newMojoExecution());
    Map<String, String> state = TestResources.readProperties(basedir, "target/workspacestate.properties");

    Assert.assertEquals(new File(basedir, "pom.xml").getCanonicalPath(), state.get("test:test:pom::1"));
    Assert.assertEquals(new File(basedir, "target/classes").getCanonicalPath(), state.get("test:test:jar::1"));
  }

  private HashCode sha1(File basedir, String path) throws IOException {
    return Files.hash(new File(basedir, path), Hashing.sha1());
  }

  private Map<String, String> readProperties(File basedir) throws IOException {
    return TestResources.readProperties(basedir, "target/test-classes/test.properties");
  }

  @Test
  public void testWorkspaceResolver() throws Exception {
    File basedir = resources.getBasedir();
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);
    mojos.newDependency(basedir).setGroupId("io.takari.m2e.workspace").setArtifactId("org.eclipse.m2e.workspace.cli").addTo(project);
    mojos.executeMojo(session, project, newMojoExecution());
    Map<String, String> properties = readProperties(basedir);
    Assert.assertEquals(basedir.getCanonicalPath(), properties.get("workspaceResolver"));
  }

  @Test
  public void testDependencyProperties() throws Exception {
    File basedir = resources.getBasedir();
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);

    Assert.assertTrue(new File(basedir, "src/test").mkdirs());

    try (OutputStream os = new FileOutputStream(new File(basedir, "src/test/test.properties"))) {
      BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os, Charsets.UTF_8));
      w.write("ga=${g:a}");
      w.newLine();
      w.write("ga_tests=${g:a:tests}");
      w.newLine();
      w.flush();
    }

    File ga = temp.newFile().getCanonicalFile();
    File ga_tests = temp.newFile().getCanonicalFile();

    mojos.newDependency(ga).setGroupId("g").setArtifactId("a").addTo(project);
    mojos.newDependency(ga_tests).setGroupId("g").setArtifactId("a").setClassifier("tests").addTo(project);

    mojos.executeMojo(session, project, "testProperties");

    Map<String, String> properties = readProperties(basedir);
    Assert.assertEquals(ga.getCanonicalPath().replace('\\', '/'), properties.get("ga"));
    Assert.assertEquals(ga_tests.getCanonicalPath().replace('\\', '/'), properties.get("ga_tests"));
  }

  @Test
  public void testClasspathScope() throws Exception {
    File basedir = resources.getBasedir();
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);

    File providedScoped = temp.newFile("provided.jar").getCanonicalFile();
    File testScoped = temp.newFile("test.jar").getCanonicalFile();

    mojos.newDependency(providedScoped).setGroupId("g").setArtifactId("provided").setScope("provided").addTo(project);
    mojos.newDependency(testScoped).setGroupId("g").setArtifactId("test").setScope("test").addTo(project);

    mojos.executeMojo(session, project, "testProperties");

    Map<String, String> properties = readProperties(basedir);
    Assert.assertEquals(new File(basedir, "target/classes").getCanonicalPath(), properties.get("classpath"));
  }
}
