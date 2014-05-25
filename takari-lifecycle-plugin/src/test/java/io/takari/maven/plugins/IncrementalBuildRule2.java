package io.takari.maven.plugins;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;

public class IncrementalBuildRule2 extends IncrementalBuildRule {

  // TODO move to IncrementalBuildRule or MojoRule

  public static Xpp3Dom newParameter(String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    return child;
  }

  public void executeMojo(MavenProject project, String goal, Xpp3Dom... parameters)
      throws Exception {
    MavenSession session = newMavenSession(project);
    executeMojo(session, project, goal, parameters);
  }

  public void executeMojo(MavenSession session, MavenProject project, String goal,
      Xpp3Dom... parameters) throws Exception {
    MojoExecution execution = newMojoExecution(goal);
    if (parameters != null) {
      Xpp3Dom configuration = execution.getConfiguration();
      for (Xpp3Dom parameter : parameters) {
        configuration.addChild(parameter);
      }
    }
    executeMojo(session, project, execution);
  }

  public MavenProject readMavenProject(File basedir, Properties properties) throws Exception {
    File pom = new File(basedir, "pom.xml");
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    request.setUserProperties(properties);
    request.setBaseDirectory(basedir);
    ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
    MavenProject project = lookup(ProjectBuilder.class).build(pom, configuration).getProject();
    Assert.assertNotNull(project);
    return project;
  }

  public static void create(File basedir, String... paths) throws IOException {
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
