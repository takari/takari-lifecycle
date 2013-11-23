package io.tesla.maven.plugins.resources;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.maven.plugin.Conf;
import org.sonatype.maven.plugin.LifecycleGoal;
import org.sonatype.maven.plugin.LifecyclePhase;

@LifecycleGoal(goal = "process-test-resources", phase = LifecyclePhase.PROCESS_TEST_RESOURCES)
public class ProcessTestResources extends ProcessResources {

  @Conf(defaultValue = "${project.build.testOutputDirectory}", property = "resources.testOutputDirectory")
  protected File testOutputDirectory;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    process(project.getBuild().getTestResources(), testOutputDirectory);
  }
}
