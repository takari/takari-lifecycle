package io.takari.maven.plugins.resources;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static io.takari.maven.testing.TestResources.assertFileContents;
import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.rm;

import java.io.File;
import java.nio.charset.Charset;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.io.Files;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

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
  public void resourcesWithProjectFilters() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-resources-filters");
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

  @Test
  public void testIncremental() throws Exception {
    File basedir = resources.getBasedir("resources/resources-incremental");

    mojos.executeMojo(basedir, "process-resources");
    mojos.assertBuildOutputs(basedir, "target/classes/resource.txt");

    // no change rebuild
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertCarriedOverOutputs(basedir, "target/classes/resource.txt");

    // pom.xml change, non-filtered resources are carried over as-is
    cp(basedir, "pom.xml-description", "pom.xml");
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertCarriedOverOutputs(basedir, "target/classes/resource.txt");

    // resource change
    cp(basedir, "resource.txt-changed", "src/main/resources/resource.txt");
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertBuildOutputs(basedir, "target/classes/resource.txt");

    // resource delete
    rm(basedir, "src/main/resources/resource.txt");
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertDeletedOutputs(basedir, "target/classes/resource.txt");
  }

  @Test
  public void testIncrementalFiltering() throws Exception {
    File basedir = resources.getBasedir("resources/resources-incremental-filtering");

    mojos.executeMojo(basedir, "process-resources");
    mojos.assertBuildOutputs(basedir, "target/classes/filtered-resource.txt");
    assertFileContents(basedir, "expected/filtered-resource.txt", "target/classes/filtered-resource.txt");

    // no change rebuild, note that filtered resources are always processed
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertBuildOutputs(basedir, "target/classes/filtered-resource.txt");
    assertFileContents(basedir, "expected/filtered-resource.txt", "target/classes/filtered-resource.txt");

    // pom change
    cp(basedir, "pom.xml-description", "pom.xml");
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertBuildOutputs(basedir, "target/classes/filtered-resource.txt");
    assertFileContents(basedir, "expected/filtered-resource.txt-pomChanged", "target/classes/filtered-resource.txt");

    // resource change
    cp(basedir, "filtered-resource.txt-changed", "src/main/resources/filtered-resource.txt");
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertBuildOutputs(basedir, "target/classes/filtered-resource.txt");
    assertFileContents(basedir, "expected/filtered-resource.txt-resourceChanged", "target/classes/filtered-resource.txt");

    // resource delete
    rm(basedir, "src/main/resources/filtered-resource.txt");
    mojos.executeMojo(basedir, "process-resources");
    mojos.assertDeletedOutputs(basedir, "target/classes/filtered-resource.txt");
  }

  @Test
  public void testBinaryResource() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-binary-resources");

    mojos.executeMojo(basedir, "process-resources");
    mojos.assertBuildOutputs(basedir, "target/classes/resource.data");

    byte[] expected = Files.toByteArray(new File(basedir, "src/main/resources/resource.data"));
    byte[] actual = Files.toByteArray(new File(basedir, "target/classes/resource.data"));
    Assert.assertArrayEquals(expected, actual);
  }
}
