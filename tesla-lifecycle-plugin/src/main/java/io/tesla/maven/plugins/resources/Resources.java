package io.tesla.maven.plugins.resources;

import io.tesla.maven.plugins.TeslaLifecycleMojo;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Properties;

import javax.inject.Inject;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.tesla.incremental.BuildContext;
import org.eclipse.tesla.incremental.DefaultFileSet;
import org.eclipse.tesla.incremental.FileSetBuilder;
import org.sonatype.maven.plugin.Conf;
import org.sonatype.maven.plugin.LifecycleGoal;
import org.sonatype.maven.plugin.LifecyclePhase;

import com.google.common.io.CharStreams;
import com.google.common.io.Closer;

@LifecycleGoal(goal = "process-resources", phase = LifecyclePhase.PROCESS_RESOURCES)
public class Resources extends TeslaLifecycleMojo {

  @Conf(defaultValue = "${project.build.outputDirectory}", property = "resources.outputDirectory")
  private File outputDirectory;

  @Conf(defaultValue = "${basedir}")
  private File basedir;

  @Conf(defaultValue = "${project.properties}")
  private Properties properties;

  @Inject
  private BuildContext buildContext;

  private final ResourceFilter resourceFilter;

  public Resources() {
    resourceFilter = new ResourceFilter();
  }

  @Override
  protected void executeMojo() throws MojoExecutionException {
    List<Resource> resourceDirectories = project.getBuild().getResources();
    for (Resource resource : resourceDirectories) {
      boolean filter = Boolean.parseBoolean(resource.getFiltering());
      // Resource directories are already pre-pended with ${project.basedir}
      File inputDir = new File(resource.getDirectory());
      List<String> includes = resource.getIncludes();
      if(includes.isEmpty()) {
        includes.add("**/**");
      }
      DefaultFileSet fileSet = new FileSetBuilder(inputDir) //
          .addIncludes(includes) //
          .addExcludes(resource.getExcludes()) //
          .build();
      for (File inputFile : buildContext.registerInputs(fileSet)) {
        buildContext.addProcessedInput(inputFile);
        File outputFile;
        if (resource.getTargetPath() != null) {
          // A custom location within the outputDirectory, the targetPath is relative to the outputDirectory
          outputFile = fileSet.relativize(new File(outputDirectory, resource.getTargetPath()), inputFile);
        } else {
          // Use the default output directory
          outputFile = fileSet.relativize(outputDirectory, inputFile);
        }
        Closer closer = Closer.create();
        try {
          try {
            Reader reader = closer.register(new FileReader(inputFile));
            Writer writer = closer.register(new OutputStreamWriter(buildContext.newOutputStream(inputFile, outputFile)));
            if (filter) {
              resourceFilter.filter(reader, writer, properties);
            } else {
              CharStreams.copy(reader, writer);
            }
          } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
          } finally {
            closer.close();
          }
        } catch (IOException e) {
          throw new MojoExecutionException(e.getMessage(), e);
        }
      }
    }
  }
}
