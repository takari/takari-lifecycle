package io.takari.maven.plugins.install_deploy;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
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

    executeMojo(project, "jar", newParameter("sourceJar", "true"), newParameter("testJar", "true"));
    Assert.assertEquals(2, project.getAttachedArtifacts().size());

    MavenSession session = newSession(project, localrepo);

    executeMojo(session, project, "install");
    Assert.assertTrue(new File(localrepo, "io/takari/lifecycle/its/test/1.0/test-1.0.jar")
        .canRead());
    Assert.assertTrue(new File(localrepo, "io/takari/lifecycle/its/test/1.0/test-1.0.pom")
        .canRead());
    Assert.assertTrue(new File(localrepo, "io/takari/lifecycle/its/test/1.0/test-1.0-sources.jar")
        .canRead());
    Assert.assertTrue(new File(localrepo, "io/takari/lifecycle/its/test/1.0/test-1.0-tests.jar")
        .canRead());

    executeMojo(session, project, "deploy");
    Assert.assertTrue(new File(remoterepo, "io/takari/lifecycle/its/test/1.0/test-1.0.jar")
        .canRead());
    Assert.assertTrue(new File(remoterepo, "io/takari/lifecycle/its/test/1.0/test-1.0.pom")
        .canRead());
    Assert.assertTrue(new File(remoterepo, "io/takari/lifecycle/its/test/1.0/test-1.0-sources.jar")
        .canRead());
    Assert.assertTrue(new File(remoterepo, "io/takari/lifecycle/its/test/1.0/test-1.0-tests.jar")
        .canRead());
  }


  // TODO move to a shared helper, possibly IncrementalBuildRule or MojoRule

  protected MavenSession newSession(MavenProject project, File localrepo)
      throws NoLocalRepositoryManagerException {
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    MavenExecutionResult result = new DefaultMavenExecutionResult();
    DefaultRepositorySystemSession repoSession = MavenRepositorySystemUtils.newSession();
    LocalRepository localRepo = new LocalRepository(localrepo);
    repoSession.setLocalRepositoryManager(new SimpleLocalRepositoryManagerFactory().newInstance(
        repoSession, localRepo));
    MavenSession session = new MavenSession(mojos.getContainer(), repoSession, request, result);
    session.setCurrentProject(project);
    session.setProjects(Arrays.asList(project));
    return session;
  }


  protected Xpp3Dom newParameter(String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    return child;
  }

  public void executeMojo(MavenProject project, String goal, Xpp3Dom... parameters)
      throws Exception {
    MavenSession session = mojos.newMavenSession(project);
    executeMojo(session, project, goal, parameters);
  }

  public void executeMojo(MavenSession session, MavenProject project, String goal,
      Xpp3Dom... parameters) throws Exception {
    MojoExecution execution = mojos.newMojoExecution(goal);
    if (parameters != null) {
      Xpp3Dom configuration = execution.getConfiguration();
      for (Xpp3Dom parameter : parameters) {
        configuration.addChild(parameter);
      }
    }
    mojos.executeMojo(session, project, execution);
  }

  public MavenProject readMavenProject(File basedir, Properties properties) throws Exception {
    File pom = new File(basedir, "pom.xml");
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    request.setUserProperties(properties);
    request.setBaseDirectory(basedir);
    ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
    MavenProject project =
        mojos.lookup(ProjectBuilder.class).build(pom, configuration).getProject();
    Assert.assertNotNull(project);
    return project;
  }

  private static void create(File basedir, String... paths) throws IOException {
    if (paths == null || paths.length == 0) {
      throw new IllegalArgumentException();
    }
    for (String path : paths) {
      File file = new File(basedir, path);
      Assert.assertTrue(file.getParentFile().mkdirs());
      file.createNewFile();
      Assert.assertTrue(file.isFile() && file.canRead());
    }
  }
}
