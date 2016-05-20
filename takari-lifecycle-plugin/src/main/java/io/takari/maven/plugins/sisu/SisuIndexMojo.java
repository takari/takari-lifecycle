package io.takari.maven.plugins.sisu;

import java.io.File;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "sisu-index", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class SisuIndexMojo extends AbstractSisuIndexMojo {

  public static final String PATH_SISU_INDEX = "META-INF/sisu/javax.inject.Named";

  @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
  private File outputDirectory;

  @Parameter(defaultValue = "${project.build.outputDirectory}/" + PATH_SISU_INDEX, readonly = true)
  private File outputFile;

  @Override
  protected File getOutputDirectory() {
    return outputDirectory;
  }

  @Override
  protected File getOutputFile() {
    return outputFile;
  }
}
