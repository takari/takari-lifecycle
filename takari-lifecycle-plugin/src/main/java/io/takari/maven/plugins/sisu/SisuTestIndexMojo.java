package io.takari.maven.plugins.sisu;

import java.io.File;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "sisu-test-index", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, threadSafe = true)
public class SisuTestIndexMojo extends AbstractSisuIndexMojo {

  public static final String PATH_SISU_INDEX = "META-INF/sisu/javax.inject.Named";

  @Parameter(defaultValue = "${project.build.testOutputDirectory}", readonly = true)
  private File outputDirectory;

  @Parameter(defaultValue = "${project.build.testOutputDirectory}/" + PATH_SISU_INDEX, readonly = true)
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
