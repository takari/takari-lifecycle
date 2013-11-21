package io.tesla.maven.plugins.resources;

import java.io.File;
import java.io.IOException;
import java.util.List;

import io.tesla.maven.plugins.TeslaLifecycleMojo;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.sonatype.maven.plugin.Conf;
import org.sonatype.maven.plugin.LifecycleGoal;
import org.sonatype.maven.plugin.LifecyclePhase;

import com.google.common.base.Joiner;

@LifecycleGoal(goal = "process-resources", phase = LifecyclePhase.PROCESS_RESOURCES)
public class Resources extends TeslaLifecycleMojo {

  @Conf(defaultValue = "${project.build.outputDirectory}", property = "resources.outputDirectory")
  private File outputDirectory;

  private static Joiner joiner = Joiner.on(",").skipNulls();
  
  @Override
  protected void executeMojo() throws MojoExecutionException {
    List<Resource> resourceDirectories = project.getBuild().getResources();
    for (Resource resourceDirectory : resourceDirectories) {
      try {
        List<File> resources = FileUtils.getFiles(new File(resourceDirectory.getDirectory()), joiner.join(resourceDirectory.getIncludes()), joiner.join(resourceDirectory.getExcludes()));
        if (Boolean.parseBoolean(resourceDirectory.getFiltering())) {
          //
          // The resources need to be filtered 
          //
          for (File resource : resources) {

          }
        } else {
          //
          // Just copy the resources in the outputDirectory
          //
          for (File resource : resources) {

          }
        }
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }

}
