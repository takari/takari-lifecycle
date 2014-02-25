package io.takari.maven.plugins.resources;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "process-test-resources", defaultPhase = LifecyclePhase.PROCESS_TEST_RESOURCES)
public class ProcessTestResources extends ProcessResources {

  @Parameter(defaultValue = "${project.build.testOutputDirectory}", property = "resources.testOutputDirectory")
  protected File testOutputDirectory;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    process(project.getBuild().getTestResources(), testOutputDirectory);
  }
}
