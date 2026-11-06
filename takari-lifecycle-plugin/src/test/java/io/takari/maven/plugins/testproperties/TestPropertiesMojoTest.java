package io.takari.maven.plugins.testproperties;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static org.hamcrest.CoreMatchers.containsString;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestPropertiesMojoTest {
    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final IncrementalBuildRule mojos = new IncrementalBuildRule();

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private MojoExecution newMojoExecution(Xpp3Dom... parameters) throws IOException {
        MojoExecution execution = mojos.newMojoExecution("testProperties", parameters);
        PluginDescriptor pluginDescriptor = execution.getMojoDescriptor().getPluginDescriptor();

        ArtifactHandler handler = new DefaultArtifactHandler("jar");
        DefaultArtifact workspaceResolver = new DefaultArtifact(
                "io.takari.m2e.workspace",
                "org.eclipse.m2e.workspace.cli",
                "1",
                Artifact.SCOPE_COMPILE,
                ".jar",
                null,
                handler);
        workspaceResolver.setFile(Path.of("target/workspaceResolver.jar")
                .toAbsolutePath()
                .normalize()
                .toFile());

        List<Artifact> pluginArtifacts = new ArrayList<>(pluginDescriptor.getArtifacts());
        pluginArtifacts.add(workspaceResolver);
        pluginDescriptor.setArtifacts(pluginArtifacts);

        return execution;
    }

    @Test
    public void testIncremental() throws Exception {
        Path basedir = resources.getBasedir("testproperties/basic").toPath();
        final MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);
        session.setProjectDependencyGraph(new ProjectDependencyGraph() {
            @Override
            public List<MavenProject> getUpstreamProjects(MavenProject project, boolean transitive) {
                return List.of();
            }

            @Override
            public List<MavenProject> getSortedProjects() {
                return List.of(project);
            }

            @Override
            public List<MavenProject> getDownstreamProjects(MavenProject project, boolean transitive) {
                return List.of();
            }

            @Override
            public List<MavenProject> getAllProjects() {
                return List.of();
            }
        });

        mojos.executeMojo(session, project, newMojoExecution());
        mojos.assertBuildOutputs(
                basedir.toFile(), "target/test-classes/test.properties", "target/workspacestate.properties");

        Path testProperties = basedir.resolve("target/test-classes/test.properties");
        Path workspaceState = basedir.resolve("target/workspacestate.properties");

        FileTime testPropertiesLastmodified = Files.getLastModifiedTime(testProperties);
        FileTime workspaceStateLastmodified = Files.getLastModifiedTime(workspaceState);

        HashCode testPropertiesSha1 = sha1(testProperties);
        HashCode workspaceStateSha1 = sha1(workspaceState);

        mojos.executeMojo(session, project, newMojoExecution());
        // mojos.assertCarriedOverOutputs(basedir, "target/test-classes/test.properties",
        // "target/workspacestate.properties");

        Assert.assertEquals(testPropertiesLastmodified, Files.getLastModifiedTime(testProperties));
        Assert.assertEquals(workspaceStateLastmodified, Files.getLastModifiedTime(workspaceState));

        Assert.assertEquals(testPropertiesSha1, sha1(basedir, "target/test-classes/test.properties"));
        Assert.assertEquals(workspaceStateSha1, sha1(basedir, "target/workspacestate.properties"));
    }

    @Test
    public void testOffline() throws Exception {
        Path basedir = resources.getBasedir("testproperties/basic").toPath();

        MavenProject project = mojos.readMavenProject(basedir.toFile());
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
        Path basedir = resources.getBasedir("testproperties/basic").toPath();

        MavenProject project = mojos.readMavenProject(basedir.toFile());
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
        Path basedir = resources
                .getBasedir("testproperties/custom-test-properties-file")
                .toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);

        mojos.executeMojo(session, project, newMojoExecution());
        Assert.assertEquals("value", readProperties(basedir).get("custom"));

        TestResources.cp(basedir.toFile(), "src/test/modified-test.properties", "src/test/test.properties");
        mojos.executeMojo(session, project, newMojoExecution());
        Assert.assertEquals("modified-value", readProperties(basedir).get("custom"));
    }

    @Test
    public void testMissingTestPropertiesEmpty() throws Exception {
        Path basedir =
                resources.getBasedir("testproperties/missing-test-properties").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);

        mojos.executeMojo(session, project, newMojoExecution(newParameter("missingPropertyAction", "empty")));
        Assert.assertEquals("", readProperties(basedir).get("custom"));
    }

    @Test
    public void testMissingTestPropertiesLeave() throws Exception {
        Path basedir =
                resources.getBasedir("testproperties/missing-test-properties").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);

        mojos.executeMojo(session, project, newMojoExecution(newParameter("missingPropertyAction", "leave")));
        Assert.assertEquals("${missing}", readProperties(basedir).get("custom"));
    }

    @Test
    public void testMissingTestPropertiesFail() throws Exception {
        Path basedir =
                resources.getBasedir("testproperties/missing-test-properties").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);

        try {
            mojos.executeMojo(session, project, newMojoExecution(newParameter("missingPropertyAction", "fail")));
            Assert.fail("Should fail with missing property");
        } catch (MojoExecutionException e) {
            Assert.assertThat(e.getMessage(), containsString("Filtering: property 'missing' not found"));
        }
    }

    @Test
    public void testWorkspaceStateIncludesThisProjectJarArtifact() throws Exception {
        Path basedir = resources.getBasedir().toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);

        mojos.executeMojo(session, project, newMojoExecution());
        Map<String, String> state = TestResources.readProperties(basedir.toFile(), "target/workspacestate.properties");

        Assert.assertEquals(basedir.resolve("pom.xml").toRealPath().toString(), state.get("test:test:pom::1"));
        Assert.assertEquals(basedir.toRealPath().resolve("target/classes").toString(), state.get("test:test:jar::1"));
    }

    private HashCode sha1(Path basedir, String path) throws IOException {
        return sha1(basedir.resolve(path));
    }

    private HashCode sha1(Path file) throws IOException {
        return Hashing.sha1().hashBytes(Files.readAllBytes(file));
    }

    private Map<String, String> readProperties(Path basedir) throws IOException {
        return TestResources.readProperties(basedir.toFile(), "target/test-classes/test.properties");
    }

    @Test
    public void testWorkspaceResolver() throws Exception {
        Path basedir = resources.getBasedir().toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);
        mojos.newDependency(basedir.toFile())
                .setGroupId("io.takari.m2e.workspace")
                .setArtifactId("org.eclipse.m2e.workspace.cli")
                .addTo(project);
        mojos.executeMojo(session, project, newMojoExecution());
        Map<String, String> properties = readProperties(basedir);
        Assert.assertEquals(basedir.toRealPath().toString(), properties.get("workspaceResolver"));
    }

    @Test
    public void testDependencyProperties() throws Exception {
        Path basedir = resources.getBasedir().toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);

        Path testProperties =
                Files.createDirectories(basedir.resolve("src/test")).resolve("test.properties");
        Files.write(testProperties, List.of("ga=${g:a}", "ga_tests=${g:a:tests}"), StandardCharsets.UTF_8);

        Path ga = temp.newFile().toPath().toRealPath();
        Path ga_tests = temp.newFile().toPath().toRealPath();

        mojos.newDependency(ga.toFile()).setGroupId("g").setArtifactId("a").addTo(project);
        mojos.newDependency(ga_tests.toFile())
                .setGroupId("g")
                .setArtifactId("a")
                .setClassifier("tests")
                .addTo(project);

        mojos.executeMojo(session, project, "testProperties");

        Map<String, String> properties = readProperties(basedir);
        Assert.assertEquals(ga.toString().replace('\\', '/'), properties.get("ga"));
        Assert.assertEquals(ga_tests.toString().replace('\\', '/'), properties.get("ga_tests"));
    }

    @Test
    public void testClasspathScope() throws Exception {
        Path basedir = resources.getBasedir().toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        MavenSession session = mojos.newMavenSession(project);

        Path providedScoped = temp.newFile("provided.jar").toPath().toRealPath();
        Path testScoped = temp.newFile("test.jar").toPath().toRealPath();

        mojos.newDependency(providedScoped.toFile())
                .setGroupId("g")
                .setArtifactId("provided")
                .setScope("provided")
                .addTo(project);
        mojos.newDependency(testScoped.toFile())
                .setGroupId("g")
                .setArtifactId("test")
                .setScope("test")
                .addTo(project);

        mojos.executeMojo(session, project, "testProperties");

        Map<String, String> properties = readProperties(basedir);
        Assert.assertEquals(basedir.toRealPath().resolve("target/classes").toString(), properties.get("classpath"));
    }
}
