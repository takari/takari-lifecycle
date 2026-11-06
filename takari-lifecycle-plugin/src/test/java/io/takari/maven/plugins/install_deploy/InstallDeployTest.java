package io.takari.maven.plugins.install_deploy;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static io.takari.maven.testing.TestResources.create;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class InstallDeployTest {
    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final IncrementalBuildRule mojos = new IncrementalBuildRule();

    @Test
    public void testBasic_release() throws Exception {
        Path basedir = resources.getBasedir("install-deploy/basic").toPath();
        create(basedir.toFile(), "target/classes/resource.txt", "target/test-classes/resource.txt");

        Path localrepo = basedir.resolve("target/localrepo");
        Files.createDirectories(localrepo);

        Path remoterepo = basedir.resolve("target/remoterepo");
        Files.createDirectories(remoterepo);

        Properties properties = new Properties();
        properties.put("version", "1.0");
        properties.put("repopath", remoterepo.toRealPath().toString());
        MavenProject project = readMavenProject(basedir, properties);

        MavenSession session = newSession(project, localrepo, null);

        mojos.executeMojo(session, project, "jar", newParameter("sourceJar", "true"), newParameter("testJar", "true"));
        Assert.assertEquals(2, project.getAttachedArtifacts().size());

        mojos.executeMojo(session, project, "install");
        Assert.assertTrue(Files.isReadable(localrepo.resolve("io/takari/lifecycle/its/test/1.0/test-1.0.jar")));
        Assert.assertTrue(Files.isReadable(localrepo.resolve("io/takari/lifecycle/its/test/1.0/test-1.0.pom")));
        Assert.assertTrue(Files.isReadable(localrepo.resolve("io/takari/lifecycle/its/test/1.0/test-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(localrepo.resolve("io/takari/lifecycle/its/test/1.0/test-1.0-tests.jar")));

        mojos.executeMojo(session, project, "deploy");
        Assert.assertTrue(Files.isReadable(remoterepo.resolve("io/takari/lifecycle/its/test/1.0/test-1.0.jar")));
        Assert.assertTrue(Files.isReadable(remoterepo.resolve("io/takari/lifecycle/its/test/1.0/test-1.0.pom")));
        Assert.assertTrue(
                Files.isReadable(remoterepo.resolve("io/takari/lifecycle/its/test/1.0/test-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(remoterepo.resolve("io/takari/lifecycle/its/test/1.0/test-1.0-tests.jar")));
    }

    private MavenSession newSession(MavenProject project, Path localrepo, Properties properties) throws Exception {
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        MavenExecutionResult result = new DefaultMavenExecutionResult();
        DefaultRepositorySystemSession repoSession = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(localrepo.toFile());
        repoSession.setLocalRepositoryManager(mojos.getContainer()
                .lookup(LocalRepositoryManagerFactory.class, "simple")
                .newInstance(repoSession, localRepo));
        MavenSession session = new MavenSession(mojos.getContainer(), repoSession, request, result);
        List<MavenProject> projects = new ArrayList<>();
        projects.add(project);
        for (String module : project.getModules()) {
            MavenProject moduleProject =
                    readMavenProject(project.getBasedir().toPath().resolve(module), properties);
            moduleProject.setParent(project);
            projects.add(moduleProject);
        }

        session.setProjects(projects);
        return session;
    }

    private MavenProject readMavenProject(Path basedir, Properties properties) throws Exception {
        Path pom = basedir.resolve("pom.xml");
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setUserProperties(properties);
        request.setBaseDirectory(basedir.toFile());
        ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
        configuration.setRepositorySession(new DefaultRepositorySystemSession());
        MavenProject project = mojos.lookup(ProjectBuilder.class)
                .build(pom.toFile(), configuration)
                .getProject();
        Assert.assertNotNull(project);
        return project;
    }

    @Test
    public void testAltDeployRepository() throws Exception {
        Path basedir = resources.getBasedir("install-deploy/basic").toPath();
        create(basedir.toFile(), "target/classes/resource.txt", "target/test-classes/resource.txt");

        Path localrepo = basedir.resolve("target/localrepo");
        Files.createDirectories(localrepo);

        Path remoterepo = basedir.resolve("target/remoterepo");
        Files.createDirectories(remoterepo);

        Properties properties = new Properties();
        properties.put("version", "1.0");
        properties.put("repopath", remoterepo.toRealPath().toString());
        MavenProject project = readMavenProject(basedir, properties);

        MavenSession session = newSession(project, localrepo, null);

        mojos.executeMojo(session, project, "jar", newParameter("sourceJar", "true"), newParameter("testJar", "true"));
        Assert.assertEquals(2, project.getAttachedArtifacts().size());

        Path altDeploymentRepository = basedir.resolve("target/altremoterepo");

        mojos.executeMojo(
                session,
                project,
                "deploy",
                newParameter("altDeploymentRepository", "default::file://" + altDeploymentRepository.toAbsolutePath()));
        Assert.assertTrue(
                Files.isReadable(altDeploymentRepository.resolve("io/takari/lifecycle/its/test/1.0/test-1.0.jar")));
        Assert.assertTrue(
                Files.isReadable(altDeploymentRepository.resolve("io/takari/lifecycle/its/test/1.0/test-1.0.pom")));
        Assert.assertTrue(Files.isReadable(
                altDeploymentRepository.resolve("io/takari/lifecycle/its/test/1.0/test-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(
                altDeploymentRepository.resolve("io/takari/lifecycle/its/test/1.0/test-1.0-tests.jar")));
    }

    @Test
    public void testAltDeployRepositoryLegacy() throws Exception {
        Path basedir = resources.getBasedir("install-deploy/basic").toPath();
        create(basedir.toFile(), "target/classes/resource.txt", "target/test-classes/resource.txt");

        Path localrepo = basedir.resolve("target/localrepo");
        Files.createDirectories(localrepo);

        Path remoterepo = basedir.resolve("target/remoterepo");
        Files.createDirectories(remoterepo);

        Properties properties = new Properties();
        properties.put("version", "1.0");
        properties.put("repopath", remoterepo.toRealPath().toString());
        MavenProject project = readMavenProject(basedir, properties);

        MavenSession session = newSession(project, localrepo, null);

        mojos.executeMojo(session, project, "jar", newParameter("sourceJar", "true"), newParameter("testJar", "true"));
        Assert.assertEquals(2, project.getAttachedArtifacts().size());

        Path altDeploymentRepository = basedir.resolve("target/altremoterepo");

        mojos.executeMojo(
                session,
                project,
                "deploy",
                newParameter(
                        "altDeploymentRepository",
                        "default::default::file://" + altDeploymentRepository.toAbsolutePath()));
        Assert.assertTrue(
                Files.isReadable(altDeploymentRepository.resolve("io/takari/lifecycle/its/test/1.0/test-1.0.jar")));
        Assert.assertTrue(
                Files.isReadable(altDeploymentRepository.resolve("io/takari/lifecycle/its/test/1.0/test-1.0.pom")));
        Assert.assertTrue(Files.isReadable(
                altDeploymentRepository.resolve("io/takari/lifecycle/its/test/1.0/test-1.0-sources.jar")));
        Assert.assertTrue(Files.isReadable(
                altDeploymentRepository.resolve("io/takari/lifecycle/its/test/1.0/test-1.0-tests.jar")));
    }

    @Test
    public void testDeployAtEnd() throws Exception {
        Path basedir = resources.getBasedir("install-deploy/deploy-at-end").toPath();

        Path localrepo = basedir.resolve("target/localrepo");
        Files.createDirectories(localrepo);

        Path remoterepo = basedir.resolve("target/remoterepo");
        Files.createDirectories(remoterepo);

        Properties properties = new Properties();
        properties.put("version", "1.0");
        properties.put("repopath", remoterepo.toRealPath().toString());
        MavenProject project = readMavenProject(basedir, properties);

        MavenSession session = newSession(project, localrepo, properties);

        DeployParticipant deployParticipant = mojos.getContainer().lookup(DeployParticipant.class);

        Assert.assertEquals(0, deployParticipant.getDeployAtEndRequests().size());

        for (MavenProject reactorProject : session.getProjects()) {
            session.setCurrentProject(reactorProject);
            if (!reactorProject.getPackaging().equals("pom")) {
                mojos.executeMojo(session, reactorProject, "compile");
                mojos.executeMojo(session, reactorProject, "jar");
            }
            mojos.executeMojo(session, reactorProject, "deploy");
        }

        Assert.assertFalse(Files.isReadable(remoterepo.resolve("io/takari/lifecycle/its/test/1.0/test-1.0.pom")));
        Assert.assertFalse(
                Files.isReadable(remoterepo.resolve("io/takari/lifecycle/its/test-modulea/1.0/test-modulea-1.0.jar")));
        Assert.assertFalse(
                Files.isReadable(remoterepo.resolve("io/takari/lifecycle/its/test-modulea/1.0/test-modulea-1.0.pom")));
        Assert.assertFalse(
                Files.isReadable(remoterepo.resolve("io/takari/lifecycle/its/test-moduleb/1.0/test-moduleb-1.0.jar")));
        Assert.assertFalse(
                Files.isReadable(remoterepo.resolve("io/takari/lifecycle/its/test-moduleb/1.0/test-moduleb-1.0.pom")));

        Assert.assertEquals(3, deployParticipant.getDeployAtEndRequests().size());
    }
}
