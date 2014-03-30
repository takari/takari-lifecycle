package io.takari.maven.plugins.jar;

import io.takari.maven.plugins.TakariLifecycleMojo;
import io.tesla.proviso.archive.Archiver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.google.common.io.Closer;

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
      createManifestFile();
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
    Closer closer = Closer.create();
    try {
      OutputStream os = closer.register(new FileOutputStream(mavenPropertiesFile));
      p.store(os);
    } finally {
      closer.close();
    }
  }

  private void createManifestFile() throws IOException {
    Manifest mf = new Manifest();
    Attributes main = mf.getMainAttributes();
    main.putValue("Manifest-Version", "1.0");
    main.putValue("Archiver-Version", "Provisio Archiver");
    main.putValue("Created-By", "Takari Inc.");
    main.putValue("Built-By", System.getProperty("user.name"));
    main.putValue("Build-Jdk", System.getProperty("java.version"));
    main.putValue("Specification-Title", project.getArtifactId());
    main.putValue("Specification-Version", project.getVersion());
    main.putValue("Implementation-Title", project.getArtifactId());
    main.putValue("Implementation-Version", project.getVersion());
    main.putValue("Implementation-Vendor-Id", project.getGroupId());
    File mavenPropertiesFile = new File(classesDirectory, "META-INF/MANIFEST.MF");
    if (!mavenPropertiesFile.getParentFile().exists()) {
      mavenPropertiesFile.getParentFile().mkdirs();
    }
    Closer closer = Closer.create();
    try {
      OutputStream os = closer.register(new FileOutputStream(mavenPropertiesFile));
      mf.write(os);
    } finally {
      closer.close();
    }
  }

}
