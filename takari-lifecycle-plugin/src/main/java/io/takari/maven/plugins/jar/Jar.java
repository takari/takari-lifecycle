/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext.AggregateCreator;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext.AggregateInput;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext.AggregateOutput;
import io.takari.maven.plugins.TakariLifecycleMojo;
import io.takari.maven.plugins.util.PropertiesWriter;
import io.tesla.proviso.archive.Archiver;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.source.FileEntry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

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

  @Inject
  private AggregatorBuildContext buildContext;

  private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

  @Override
  protected void executeMojo() throws MojoExecutionException {

    if (!outputDirectory.exists()) {
      outputDirectory.mkdir();
    }

    if (mainJar) {
      File jar = new File(outputDirectory, String.format("%s.jar", finalName));
      AggregateOutput registeredOutput = buildContext.registerOutput(jar);
      try {
        if (classesDirectory.isDirectory()) {
          registeredOutput.addInputs(classesDirectory, null, null);
        } else {
          logger.warn("Main classes directory {} does not exist", classesDirectory);
        }
        // XXX this does not detect changes in archive#manifestFile.
        registeredOutput.createIfNecessary(new AggregateCreator() {
          @Override
          public void create(Output<File> output, Iterable<AggregateInput> inputs) throws IOException {
            logger.info("Building main JAR.");

            List<Iterable<Entry>> sources = new ArrayList<>();
            if (archive != null && archive.getManifestFile() != null) {
              sources.add(jarManifestSource(archive.getManifestFile()));
            }
            sources.add(inputsSource(inputs));
            sources.add(pomPropertiesSource(project));
            sources.add(jarManifestSource(project));
            archive(output.getResource(), sources);
          }
        });
        project.getArtifact().setFile(jar);
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }

    if (sourceJar) {
      File sourceJar = new File(outputDirectory, String.format("%s-%s.jar", finalName, "sources"));
      AggregateOutput registeredOutput = buildContext.registerOutput(sourceJar);
      try {
        for (String sourceRoot : project.getCompileSourceRoots()) {
          File dir = new File(sourceRoot);
          if (dir.isDirectory()) {
            registeredOutput.addInputs(new File(sourceRoot), null, null);
          }
        }
        registeredOutput.createIfNecessary(new AggregateCreator() {
          @Override
          public void create(Output<File> output, Iterable<AggregateInput> inputs) throws IOException {
            logger.info("Building source Jar.");

            archive(output.getResource(), asList(inputsSource(inputs), jarManifestSource(project)));
          }
        });
        projectHelper.attachArtifact(project, "jar", "sources", sourceJar);
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }

    if (testJar && testClassesDirectory.isDirectory()) {
      File testJar = new File(outputDirectory, String.format("%s-%s.jar", finalName, "tests"));
      AggregateOutput registeredOutput = buildContext.registerOutput(testJar);
      try {
        if (testClassesDirectory.isDirectory()) {
          registeredOutput.addInputs(testClassesDirectory, null, null);
        } else {
          logger.warn("Test classes directory {} does not exist", classesDirectory);
        }
        registeredOutput.createIfNecessary(new AggregateCreator() {
          @Override
          public void create(Output<File> output, Iterable<AggregateInput> inputs) throws IOException {
            logger.info("Building test JAR.");

            archive(output.getResource(), asList(inputsSource(inputs), jarManifestSource(project)));
          }
        });
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
      projectHelper.attachArtifact(project, "jar", "tests", testJar);
    }
  }

  private void archive(File jar, List<Iterable<Entry>> sources) throws IOException {
    Archiver archiver = Archiver.builder() //
        .useRoot(false) //
        .build();
    archiver.archive(jar, new AggregateSource(sources));
  }

  static String getRelativePath(File basedir, File resource) {
    return basedir.toPath().relativize(resource.toPath()).toString().replace('\\', '/'); // always use forward slash for path separator
  }

  private Iterable<Entry> inputsSource(Iterable<AggregateInput> inputs) {
    final List<Entry> entries = new ArrayList<>();
    for (AggregateInput input : inputs) {
      String entryName = getRelativePath(input.getBasedir(), input.getResource());
      entries.add(new FileEntry(entryName, input.getResource()));
    }
    return entries;
  }

  public static Iterable<Entry> jarManifestSource(File file) {
    return singleton((Entry) new FileEntry(MANIFEST_PATH, file));
  }

  private Iterable<Entry> jarManifestSource(MavenProject project) throws IOException {
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

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    manifest.write(buf);

    return singleton((Entry) new BytesEntry(MANIFEST_PATH, buf.toByteArray()));
  }

  private Iterable<Entry> pomPropertiesSource(MavenProject project) throws IOException {
    String entryName = String.format("META-INF/maven/%s/%s/pom.properties", project.getGroupId(), project.getArtifactId());

    Properties properties = new Properties();
    properties.setProperty("groupId", project.getGroupId());
    properties.setProperty("artifactId", project.getArtifactId());
    properties.setProperty("version", project.getVersion());
    File mavenPropertiesFile = new File(project.getBuild().getDirectory(), "pom.properties");
    if (!mavenPropertiesFile.getParentFile().exists()) {
      mavenPropertiesFile.getParentFile().mkdirs();
    }
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PropertiesWriter.write(properties, null, buf);

    return singleton((Entry) new BytesEntry(entryName, buf.toByteArray()));
  }

}
