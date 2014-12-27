package io.takari.maven.plugins.resources;

import static io.takari.maven.testing.TestResources.assertFileContents;
import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

import java.io.File;
import java.nio.charset.Charset;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.io.Files;

public class TestResourcesTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  @Test
  public void testResources() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-test-resources");
    mojos.executeMojo(basedir, "process-test-resources");
    File resource = new File(basedir, "target/test-classes/resource.txt");
    Assert.assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    Assert.assertTrue(line.contains("resource.txt"));
  }

  @Test
  public void testResourcesWithTargetPath() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-test-resources-with-target-path");
    mojos.executeMojo(basedir, "process-test-resources");
    File resource = new File(basedir, "target/test-classes/resources/targetPath/resource.txt");
    Assert.assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    Assert.assertTrue(line.contains("resource.txt"));
  }

  @Test
  public void testResourcesWithFiltering() throws Exception {
    File basedir = resources.getBasedir("resources/project-with-test-resources-filtered");
    mojos.executeMojo(basedir, "process-test-resources");
    assertFileContents(basedir, "expected-resource.txt", "target/test-classes/resource.txt");
  }
}
