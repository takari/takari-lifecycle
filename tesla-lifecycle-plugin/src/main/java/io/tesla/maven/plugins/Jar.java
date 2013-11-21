package io.tesla.maven.plugins;

import io.tesla.proviso.archive.Archiver;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.sonatype.maven.plugin.Conf;
import org.sonatype.maven.plugin.LifecycleGoal;
import org.sonatype.maven.plugin.LifecyclePhase;

@LifecycleGoal(goal = "jar", phase = LifecyclePhase.PACKAGE)
public class Jar extends TeslaLifecycleMojo {

  @Conf(defaultValue = "${project.build.outputDirectory}")
  private File classesDirectory;

  @Conf(defaultValue = "${project.build.finalName}")
  private String finalName;

  @Conf(defaultValue = "${project.build.directory}")
  private File outputDirectory;

  @Conf(defaultValue = "false", property = "testJar")
  private boolean testJar;

  @Conf(defaultValue = "${project.build.testOutputDirectory}")
  private File testClassesDirectory;

  @Override
  protected void executeMojo() throws MojoExecutionException {

    Archiver archiver = Archiver.builder() //
      .useRoot(false) // Step into the classes/ directory
      .build();

    //
    // type = test-jar
    // classifier = tests
    //
    File jar = new File(outputDirectory, String.format("%s.jar", finalName));
    try {
      archiver.archive(jar, classesDirectory);
      project.getArtifact().setFile(jar);
    } catch (IOException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }

    if (testJar && testClassesDirectory.exists()) {

      Archiver testArchiver = Archiver.builder() //
        .useRoot(false) // Step into the classes/ directory
        .build();

      //
      // type = test-jar
      // classifier = tests
      //
      File testJar = new File(outputDirectory, String.format("%s-%s.jar", finalName, "tests"));
      try {
        testArchiver.archive(testJar, testClassesDirectory);
        projectHelper.attachArtifact(project, "test-jar", "tests", testJar);
        project.getArtifact().setFile(testJar);
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }
}
