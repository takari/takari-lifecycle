package io.tesla.maven.plugins.resources;

import java.io.File;
import java.nio.charset.Charset;

import org.eclipse.tesla.incremental.maven.testing.AbstractBuildAvoidanceTest;

import com.google.common.io.Files;

public class ResourcesBuildAvoidanceTest extends AbstractBuildAvoidanceTest {
  
  public void testResources() throws Exception {
    File basedir = getBasedir("src/test/projects/project-with-resources");
    executeMojo(basedir, "process-resources");
    File resource = new File(basedir, "target/classes/resource.txt");
    assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    assertTrue(line.contains("resource.txt"));
  }

  public void testResourcesWithTargetPath() throws Exception {
    File basedir = getBasedir("src/test/projects/project-with-resources-with-target-path");
    executeMojo(basedir, "process-resources");
    File resource = new File(basedir, "target/classes/resources/targetPath/resource.txt");
    assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    assertTrue(line.contains("resource.txt"));
  }

  public void testResourcesWithFiltering() throws Exception {
    File basedir = getBasedir("src/test/projects/project-with-resources-filtered");
    executeMojo(basedir, "process-resources");
    assertTrue(new File(basedir, "target/classes/resource.txt").exists());
    File resource = new File(basedir, "target/classes/resource.txt");
    assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    assertTrue(line.contains("resource.txt with takari"));
  }  
}