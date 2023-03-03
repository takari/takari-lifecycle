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
import static io.takari.maven.plugins.TakariLifecycles.isJarProducingTakariLifecycle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputAggregator;
import io.takari.incrementalbuild.aggregator.InputSet;
import io.takari.maven.plugins.TakariLifecycleMojo;
import io.takari.maven.plugins.util.PropertiesWriter;
import ca.vanzyl.provisio.archive.Archiver;
import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import ca.vanzyl.provisio.archive.source.FileEntry;

@Mojo(name = "jar", defaultPhase = LifecyclePhase.PACKAGE, configurator = "takari", threadSafe = true)
public class Jar extends TakariLifecycleMojo {

  @Parameter(defaultValue = "${project.build.outputDirectory}")
  protected File classesDirectory;

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
      InputSet registeredOutput = buildContext.newInputSet();
      try {
        if (classesDirectory.isDirectory()) {
          classesDirectory = classesDirectory.getCanonicalFile();
          Iterable<File> inputs = registeredOutput.addInputs(classesDirectory, null, null);
          logger.debug("Analyzing main classes directory {} with {} entries", classesDirectory, size(inputs));
        } else {
          logger.warn("Main classes directory {} does not exist", classesDirectory);
        }
        // XXX this does not detect changes in archive#manifestFile.
        boolean processingRequired = registeredOutput.aggregateIfNecessary(jar, new InputAggregator() {
          @Override
          public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException {
            logger.info("Building main JAR.");

            List<Iterable<ExtendedArchiveEntry>> sources = new ArrayList<>();
            if (archive != null && archive.getManifestFile() != null) {
              sources.add(jarManifestSource(archive.getManifestFile()));
            }
            sources.add(inputsSource(classesDirectory, inputs));
            sources.add(singleton(pomPropertiesSource(project)));
            sources.add(jarManifestSource(project));
            archive(output.getResource(), sources);
          }
        });
        if (!processingRequired) {
          logger.info("Main JAR is up-to-date");
        }

        if (!isJarProducingTakariLifecycle(project.getPackaging()) && alternateLifecycleProvidingPrimaryArtifact()) {
          // We have a non-Takari lifecycle and a property in the MavenSession has been explicity set to indicate the lifecycle
          // executing will produce the primary artifact.
          projectHelper.attachArtifact(project, "jar", jar);
          logger.info("The '{}' lifecycle has indicated it will produce the primary artifact. Attaching JAR as a secondary artifact.", project.getPackaging());
        } else {
          // We have a standard Takari lifecycle so set the JAR created here as the primary artifact.
          project.getArtifact().setFile(jar);
        }
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }

    if (sourceJar) {
      final Multimap<File, File> sources = HashMultimap.create();
      File sourceJar = new File(outputDirectory, String.format("%s-%s.jar", finalName, "sources"));
      InputSet registeredOutput = buildContext.newInputSet();
      try {
        for (String sourceRoot : project.getCompileSourceRoots()) {
          File dir = new File(sourceRoot);
          if (dir.isDirectory()) {
            dir = dir.getCanonicalFile();
            Iterable<File> inputs = registeredOutput.addInputs(dir, null, null);
            logger.debug("Analyzing sources directory {} with {} entries", dir, size(inputs));
            sources.putAll(dir, inputs);
          } else {
            logger.debug("Sources directory {} does not exist", dir);
          }
        }
        boolean processingRequired = registeredOutput.aggregateIfNecessary(sourceJar, new InputAggregator() {
          @Override
          public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException {
            logger.info("Building source JAR.");

            archive(output.getResource(), asList(inputsSource(sources), jarManifestSource(project)));
          }
        });
        if (!processingRequired) {
          logger.info("Source JAR is up-to-date");
        }
        projectHelper.attachArtifact(project, "jar", "sources", sourceJar);
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }

    if (testJar && testClassesDirectory.isDirectory()) {
      File testJar = new File(outputDirectory, String.format("%s-%s.jar", finalName, "tests"));
      InputSet registeredOutput = buildContext.newInputSet();
      try {
        testClassesDirectory = testClassesDirectory.getCanonicalFile();
        Iterable<File> inputs = registeredOutput.addInputs(testClassesDirectory, null, null);
        logger.debug("Analyzing test classes directory {} with {} entries", testClassesDirectory, size(inputs));
        boolean processingRequired = registeredOutput.aggregateIfNecessary(testJar, new InputAggregator() {
          @Override
          public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException {
            logger.info("Building test JAR.");

            archive(output.getResource(), asList(inputsSource(testClassesDirectory, inputs), jarManifestSource(project)));
          }
        });
        if (!processingRequired) {
          logger.info("Test JAR is up-to-date");
        }
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
      projectHelper.attachArtifact(project, "jar", "tests", testJar);
    }
  }

  private void archive(File jar, List<Iterable<ExtendedArchiveEntry>> sources) throws IOException {
    Archiver archiver = Archiver.builder() //
        .useRoot(false) //
        .normalize(true) //
        .build();
    archiver.archive(jar, new AggregateSource(sources));
    if (logger.isDebugEnabled()) {
      int size = 0;
      for (Iterable<?> source : sources) {
        size += size(source);
      }
      logger.debug("Created archive {} with {} entries", jar, size);
    }
  }

  static String getRelativePath(File basedir, File resource) {
    return basedir.toPath().relativize(resource.toPath()).toString().replace('\\', '/'); // always use forward slash for path separator
  }

  private List<ExtendedArchiveEntry> inputsSource(Multimap<File, File> inputs) {
    final List<ExtendedArchiveEntry> entries = new ArrayList<>();
    for (File basedir : inputs.keySet()) {
      entries.addAll(inputsSource(basedir, inputs.get(basedir)));
    }
    return entries;
  }

  private List<ExtendedArchiveEntry> inputsSource(File basedir, Iterable<File> inputs) {
    final List<ExtendedArchiveEntry> entries = new ArrayList<>();
    for (File input : inputs) {
      String entryName = getRelativePath(basedir, input);
      entries.add(new FileEntry(entryName, input));
    }
    return entries;
  }

  public static Iterable<ExtendedArchiveEntry> jarManifestSource(File file) {
    return singleton((ExtendedArchiveEntry) new FileEntry(MANIFEST_PATH, file));
  }

  private Iterable<ExtendedArchiveEntry> jarManifestSource(MavenProject project) throws IOException {
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

    if (archive != null && archive.getManifestEntries() != null) {
      for (Map.Entry<String, String> extra : archive.getManifestEntries().entrySet()) {
        if (extra.getValue() != null) {
          main.putValue(extra.getKey(), extra.getValue());
        }
      }
    }

    File manifestFile = new File(project.getBuild().getDirectory(), "MANIFEST.MF");
    if (!manifestFile.getParentFile().exists()) {
      manifestFile.getParentFile().mkdirs();
    }

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    manifest.write(buf);

    return singleton((ExtendedArchiveEntry) new BytesEntry(MANIFEST_PATH, buf.toByteArray()));
  }

  protected ExtendedArchiveEntry pomPropertiesSource(MavenProject project) throws IOException {
    String entryName = String.format("META-INF/maven/%s/%s/pom.properties", project.getGroupId(), project.getArtifactId());

    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    Properties properties = new Properties();
    properties.setProperty("groupId", project.getGroupId());
    properties.setProperty("artifactId", project.getArtifactId());
    properties.setProperty("version", project.getVersion());
    PropertiesWriter.write(properties, null, buf);

    return new BytesEntry(entryName, buf.toByteArray());
  }

}
