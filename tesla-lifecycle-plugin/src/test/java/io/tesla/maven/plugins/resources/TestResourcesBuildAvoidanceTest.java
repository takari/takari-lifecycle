package io.tesla.maven.plugins.resources;

import java.io.File;
import java.nio.charset.Charset;

import org.eclipse.tesla.incremental.maven.testing.AbstractBuildAvoidanceTest;

import com.google.common.io.Files;

public class TestResourcesBuildAvoidanceTest extends AbstractBuildAvoidanceTest {
  
  public void testResources() throws Exception {
    File basedir = getBasedir("src/test/projects/project-with-test-resources");
    executeMojo(basedir, "process-test-resources");
    File resource = new File(basedir, "target/test-classes/resource.txt");
    assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    assertTrue(line.contains("resource.txt"));
  }

  public void testResourcesWithTargetPath() throws Exception {
    File basedir = getBasedir("src/test/projects/project-with-test-resources-with-target-path");
    executeMojo(basedir, "process-test-resources");
    File resource = new File(basedir, "target/test-classes/resources/targetPath/resource.txt");
    assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    assertTrue(line.contains("resource.txt"));
  }

  public void testResourcesWithFiltering() throws Exception {
    File basedir = getBasedir("src/test/projects/project-with-test-resources-filtered");
    executeMojo(basedir, "process-test-resources");
    File resource = new File(basedir, "target/test-classes/resource.txt");
    assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    assertTrue(line.contains("resource.txt with takari"));
  }  
}