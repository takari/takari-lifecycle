package io.takari.maven.plugins.resources;

import static org.apache.maven.plugin.testing.MojoParameters.newParameter;
import static org.apache.maven.plugin.testing.resources.TestResources.assertFileContents;
import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.io.File;
import java.nio.charset.Charset;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.io.Files;

public class ResourcesTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  @Test
  public void resources() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-resources");
    mojos.executeMojo(basedir, "process-resources");
    File resource = new File(basedir, "target/classes/resource.txt");
    Assert.assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    Assert.assertTrue(line.contains("resource.txt"));
  }

  @Test
  public void resources_skip() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-resources");
    File resource = new File(basedir, "target/classes/resource.txt");

    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);

    MojoExecution execution = mojos.newMojoExecution("process-resources");
    execution.getConfiguration().addChild(newParameter("skip", "true"));
    mojos.executeMojo(session, project, execution);
    Assert.assertFalse(resource.exists());

    mojos.executeMojo(basedir, "process-resources");
    Assert.assertTrue(resource.exists());

    execution = mojos.newMojoExecution("process-resources");
    execution.getConfiguration().addChild(newParameter("skip", "true"));
    mojos.executeMojo(session, project, execution);
    Assert.assertTrue(resource.exists());
  }

  @Test
  public void resourcesWithTargetPath() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-resources-with-target-path");
    mojos.executeMojo(basedir, "process-resources");
    File resource = new File(basedir, "target/classes/resources/targetPath/resource.txt");
    Assert.assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    Assert.assertTrue(line.contains("resource.txt"));
  }

  @Test
  public void resourcesWithFiltering() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-resources-filtered");
    mojos.executeMojo(basedir, "process-resources");
    assertFileContents(basedir, "expected-resource.txt", "target/classes/resource.txt");
  }

  @Test
  public void testCustomResources() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-custom-resources");
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertBuildOutputs(basedir, "target/custom/custom.txt");
  }

  @Test
  public void testRelativeResourcesDirectory() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-relative-resources-directory");
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertBuildOutputs(basedir, "target/custom/custom.txt");
  }
}
