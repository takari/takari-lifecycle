package io.takari.maven.plugins.jar;

import io.takari.maven.plugins.TakariLifecycleMojo;
import io.tesla.proviso.archive.Archiver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "jar", defaultPhase = LifecyclePhase.PACKAGE)
public class Jar extends TakariLifecycleMojo {

  @Parameter(defaultValue = "${project.build.outputDirectory}")
  private File classesDirectory;

  @Parameter(defaultValue = "${project.build.finalName}")
  private String finalName;

  @Parameter(defaultValue = "${project.build.directory}")
  private File outputDirectory;

  @Parameter(defaultValue = "false", property = "testJar")
  private boolean testJar;

  @Parameter(defaultValue = "${project.build.testOutputDirectory}")
  private File testClassesDirectory;

  @Override
  protected void executeMojo() throws MojoExecutionException {

    Archiver archiver = Archiver.builder() //
        .useRoot(false) // Step into the classes/ directory
        .build();

    if (!outputDirectory.exists()) {
      outputDirectory.mkdir();
    }

    //
    // type = test-jar
    // classifier = tests
    //
    File jar = new File(outputDirectory, String.format("%s.jar", finalName));
    try {
      createPomPropertiesFile();
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

  private void createPomPropertiesFile() throws IOException {
    JarProperties p = new JarProperties();
    p.setProperty("groupId", project.getGroupId());
    p.setProperty("artifactId", project.getArtifactId());
    p.setProperty("version", project.getVersion());
    File mavenPropertiesFile =
        new File(classesDirectory, String.format("META-INF/%s/%s/pom.properties",
            project.getGroupId(), project.getArtifactId()));
    if (!mavenPropertiesFile.getParentFile().exists()) {
      mavenPropertiesFile.getParentFile().mkdirs();
    }
    OutputStream os = new FileOutputStream(mavenPropertiesFile);
    p.store(os);
  }
}
