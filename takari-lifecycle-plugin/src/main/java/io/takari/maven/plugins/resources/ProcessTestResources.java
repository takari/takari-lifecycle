package io.takari.maven.plugins.resources;

import java.io.File;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "process-test-resources", defaultPhase = LifecyclePhase.PROCESS_TEST_RESOURCES)
public class ProcessTestResources extends AbstractProcessResourcesMojo {

  @Parameter(defaultValue = "${project.build.testOutputDirectory}", property = "resources.testOutputDirectory")
  private File testOutputDirectory;

  @Parameter(defaultValue = "${project.build.testResources}")
  private List<Resource> testResources;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    process(testResources, testOutputDirectory);
  }
}
