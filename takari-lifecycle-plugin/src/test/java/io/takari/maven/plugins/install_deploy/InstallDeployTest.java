package io.takari.maven.plugins.install_deploy;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static io.takari.maven.testing.TestResources.create;
import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

import java.io.File;
import java.util.Arrays;
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
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
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
    File basedir = resources.getBasedir("install-deploy/basic");
    create(basedir, "target/classes/resource.txt", "target/test-classes/resource.txt");

    File localrepo = new File(basedir, "target/localrepo");
    Assert.assertTrue(localrepo.mkdirs());

    File remoterepo = new File(basedir, "target/remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    Properties properties = new Properties();
    properties.put("version", "1.0");
    properties.put("repopath", remoterepo.getCanonicalPath());
    MavenProject project = readMavenProject(basedir, properties);

    MavenSession session = newSession(project, localrepo);

    mojos.executeMojo(session, project, "jar", newParameter("sourceJar", "true"), newParameter("testJar", "true"));
    Assert.assertEquals(2, project.getAttachedArtifacts().size());


    mojos.executeMojo(session, project, "install");
    Assert.assertTrue(new File(localrepo, "io/takari/lifecycle/its/test/1.0/test-1.0.jar").canRead());
    Assert.assertTrue(new File(localrepo, "io/takari/lifecycle/its/test/1.0/test-1.0.pom").canRead());
    Assert.assertTrue(new File(localrepo, "io/takari/lifecycle/its/test/1.0/test-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(localrepo, "io/takari/lifecycle/its/test/1.0/test-1.0-tests.jar").canRead());

    mojos.executeMojo(session, project, "deploy");
    Assert.assertTrue(new File(remoterepo, "io/takari/lifecycle/its/test/1.0/test-1.0.jar").canRead());
    Assert.assertTrue(new File(remoterepo, "io/takari/lifecycle/its/test/1.0/test-1.0.pom").canRead());
    Assert.assertTrue(new File(remoterepo, "io/takari/lifecycle/its/test/1.0/test-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(remoterepo, "io/takari/lifecycle/its/test/1.0/test-1.0-tests.jar").canRead());
  }

  private MavenSession newSession(MavenProject project, File localrepo) throws NoLocalRepositoryManagerException, ComponentLookupException {
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    MavenExecutionResult result = new DefaultMavenExecutionResult();
    DefaultRepositorySystemSession repoSession = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepo = new LocalRepository(localrepo);
    repoSession.setLocalRepositoryManager(mojos.getContainer().lookup(LocalRepositoryManagerFactory.class, "simple").newInstance(repoSession, localRepo));
    MavenSession session = new MavenSession(mojos.getContainer(), repoSession, request, result);
    session.setCurrentProject(project);
    session.setProjects(Arrays.asList(project));
    return session;
  }

  private MavenProject readMavenProject(File basedir, Properties properties) throws Exception {
    File pom = new File(basedir, "pom.xml");
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    request.setUserProperties(properties);
    request.setBaseDirectory(basedir);
    ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
    configuration.setRepositorySession(new DefaultRepositorySystemSession());
    MavenProject project = mojos.lookup(ProjectBuilder.class).build(pom, configuration).getProject();
    Assert.assertNotNull(project);
    return project;
  }

}
