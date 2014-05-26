package io.takari.maven.plugins.jar;

import io.takari.maven.plugins.TakariLifecycleMojo;
import io.tesla.proviso.archive.Archiver;
import io.tesla.proviso.archive.Source;
import io.tesla.proviso.archive.source.DirectorySource;
import io.tesla.proviso.archive.source.FileSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.google.common.io.Closer;

@Mojo(name = "jar", defaultPhase = LifecyclePhase.PACKAGE)
public class Jar extends TakariLifecycleMojo {

  @Parameter(defaultValue = "${project.build.outputDirectory}")
  private File classesDirectory;

  @Parameter(defaultValue = "${project.build.finalName}")
  private String finalName;

  @Parameter(defaultValue = "${project.build.directory}")
  private File outputDirectory;

  @Parameter(defaultValue = "true", property = "mainJar")
  private boolean mainJar;

  @Parameter(defaultValue = "false", property = "sourceJar")
  private boolean sourceJar;

  @Parameter(defaultValue = "false", property = "testJar")
  private boolean testJar;

  @Parameter(defaultValue = "${project.build.testOutputDirectory}")
  private File testClassesDirectory;

  @Parameter
  private ArchiveConfiguration archive;

  @Override
  protected void executeMojo() throws MojoExecutionException {

    if (!outputDirectory.exists()) {
      outputDirectory.mkdir();
    }

    if (mainJar) {
      Archiver archiver = Archiver.builder() //
          .useRoot(false) // Step into the classes/ directory
          .build();

      File jar = new File(outputDirectory, String.format("%s.jar", finalName));
      try {
        List<Source> sources = new ArrayList<Source>();
        if (classesDirectory.isDirectory()) {
          sources.add(new DirectorySource(classesDirectory));
        } else {
          logger.warn("Main classes directory {} does not exist", classesDirectory);
        }
        sources.add(new FileSource(String.format("META-INF/maven/%s/%s/pom.properties", project.getGroupId(), project.getArtifactId()), createPomPropertiesFile(project)));
        sources.add(new FileSource("META-INF/MANIFEST.MF", getMainManifest()));
        archiver.archive(jar, sources.toArray(new Source[sources.size()]));
        project.getArtifact().setFile(jar);
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }

    if (sourceJar) {
      logger.info("Building source Jar.");
      Archiver sourceArchiver = Archiver.builder() //
          .useRoot(false) // Step into the source directories
          .build();

      File sourceJar = new File(outputDirectory, String.format("%s-%s.jar", finalName, "sources"));
      try {
        List<Source> sources = new ArrayList<Source>();
        for (String sourceRoot : project.getCompileSourceRoots()) {
          File dir = new File(sourceRoot);
          if (dir.isDirectory()) {
            sources.add(new DirectorySource(dir));
          }
        }
        sources.add(new FileSource("META-INF/MANIFEST.MF", createManifestFile(project)));

        sourceArchiver.archive(sourceJar, sources.toArray(new Source[sources.size()]));
        projectHelper.attachArtifact(project, "jar", "sources", sourceJar);
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }

    if (testJar && testClassesDirectory.isDirectory()) {
      logger.info("Building test Jar.");
      Archiver testArchiver = Archiver.builder() //
          .useRoot(false) //
          .build();

      File testJar = new File(outputDirectory, String.format("%s-%s.jar", finalName, "tests"));
      try {
        List<Source> sources = new ArrayList<Source>();
        sources.add(new DirectorySource(testClassesDirectory));
        sources.add(new FileSource("META-INF/MANIFEST.MF", createManifestFile(project)));

        testArchiver.archive(testJar, sources.toArray(new Source[sources.size()]));
        projectHelper.attachArtifact(project, "jar", "tests", testJar);
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }

  private File createPomPropertiesFile(MavenProject project) throws IOException {
    JarProperties properties = new JarProperties();
    properties.setProperty("groupId", project.getGroupId());
    properties.setProperty("artifactId", project.getArtifactId());
    properties.setProperty("version", project.getVersion());
    File mavenPropertiesFile = new File(project.getBuild().getDirectory(), "pom.properties");
    if (!mavenPropertiesFile.getParentFile().exists()) {
      mavenPropertiesFile.getParentFile().mkdirs();
    }
    Closer closer = Closer.create();
    try {
      OutputStream os = closer.register(new FileOutputStream(mavenPropertiesFile));
      properties.store(os);
    } finally {
      closer.close();
    }
    return mavenPropertiesFile;
  }

  private File createManifestFile(MavenProject project) throws IOException {
    Manifest manifest = new Manifest();
    Attributes main = manifest.getMainAttributes();
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
    File manifestFile = new File(project.getBuild().getDirectory(), "MANIFEST.MF");
    if (!manifestFile.getParentFile().exists()) {
      manifestFile.getParentFile().mkdirs();
    }
    Closer closer = Closer.create();
    try {
      OutputStream os = closer.register(new FileOutputStream(manifestFile));
      manifest.write(os);
    } finally {
      closer.close();
    }
    return manifestFile;
  }

  private File getMainManifest() throws IOException {
    if (archive != null && archive.getManifestFile() != null) {
      File manifest = archive.getManifestFile();
      if (!manifest.isFile() || !manifest.canRead()) {
        throw new IOException(String.format("Manifest %s cannot be read", manifest));
      }
      return manifest;
    }
    return createManifestFile(project);
  }
}
