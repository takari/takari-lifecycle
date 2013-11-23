package io.tesla.maven.plugins.resources;

import io.tesla.maven.plugins.TeslaLifecycleMojo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import javax.inject.Inject;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.maven.plugin.Conf;
import org.sonatype.maven.plugin.LifecycleGoal;
import org.sonatype.maven.plugin.LifecyclePhase;

@LifecycleGoal(goal = "process-resources", phase = LifecyclePhase.PROCESS_RESOURCES)
public class ProcessResources extends TeslaLifecycleMojo {

  @Conf(defaultValue = "${project.build.outputDirectory}", property = "resources.outputDirectory")
  protected File outputDirectory;

  @Conf(defaultValue = "${basedir}")
  private File basedir;

  @Conf(defaultValue = "${project.properties}")
  private Properties properties;

  @Inject
  private ResourcesProcessor processor;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    process(project.getBuild().getResources(), outputDirectory);
  }

  protected void process(List<Resource> resources, File outputDirectory) throws MojoExecutionException {
    for (Resource resource : resources) {
      boolean filter = Boolean.parseBoolean(resource.getFiltering());
      File inputDir = new File(resource.getDirectory());
      File outputDir;
      if (resource.getTargetPath() != null) {
        outputDir = new File(outputDirectory, resource.getTargetPath());
      } else {
        outputDir = outputDirectory;
      }
      try {
        if (filter) {
          processor.process(inputDir, outputDir, resource.getIncludes(), resource.getExcludes(), properties);
        } else {
          processor.process(inputDir, outputDir, resource.getIncludes(), resource.getExcludes());
        }
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }
}
