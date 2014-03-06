package io.takari.maven.plugins.resources;

import io.takari.incrementalbuild.maven.testing.BuildAvoidanceRule;

import java.io.File;
import java.nio.charset.Charset;

import org.apache.maven.plugin.testing.resources.TestResources;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.io.Files;

public class TestResourcesBuildAvoidanceTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final BuildAvoidanceRule mojos = new BuildAvoidanceRule();

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
    File resource = new File(basedir, "target/test-classes/resource.txt");
    Assert.assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    Assert.assertTrue(line.contains("resource.txt with takari"));
  }
}
