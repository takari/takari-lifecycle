package io.takari.maven.plugins.jar;

import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext.AggregateCreator;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext.AggregateInput;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext.AggregateOutput;
import io.takari.maven.plugins.TakariLifecycleMojo;
import io.tesla.proviso.archive.Archiver;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.Source;
import io.tesla.proviso.archive.source.FileEntry;
import io.tesla.proviso.archive.source.FileSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.inject.Inject;

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

  @Inject
  private AggregatorBuildContext buildContext;

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
        registeredOutput.createIfNecessary(new AggregateCreator() {
          @Override
          public void create(Output<File> output, Iterable<AggregateInput> inputs) throws IOException {
            logger.info("Building main JAR.");

            File jar = output.getResource();

            Archiver archiver = Archiver.builder() //
                .useRoot(false) // Step into the classes/ directory
                .build();

            List<Source> sources = new ArrayList<Source>();
            sources.add(new BuildContextDirectorySource(inputs));
            sources.add(new FileSource(String.format("META-INF/maven/%s/%s/pom.properties", project.getGroupId(), project.getArtifactId()), createPomPropertiesFile(project)));
            sources.add(new FileSource("META-INF/MANIFEST.MF", getMainManifest()));
            archiver.archive(jar, sources.toArray(new Source[sources.size()]));
          }
        });
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
      project.getArtifact().setFile(jar);
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

            File sourceJar = output.getResource();

            Archiver sourceArchiver = Archiver.builder() //
                .useRoot(false) // Step into the source directories
                .build();

            List<Source> sources = new ArrayList<Source>();
            sources.add(new BuildContextDirectorySource(inputs));
            sources.add(new FileSource("META-INF/MANIFEST.MF", createManifestFile(project)));
            sourceArchiver.archive(sourceJar, sources.toArray(new Source[sources.size()]));
          }
        });
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
      projectHelper.attachArtifact(project, "jar", "sources", sourceJar);
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

            File testJar = output.getResource();

            Archiver testArchiver = Archiver.builder() //
                .useRoot(false) //
                .build();

            List<Source> sources = new ArrayList<Source>();
            sources.add(new BuildContextDirectorySource(inputs));
            sources.add(new FileSource("META-INF/MANIFEST.MF", createManifestFile(project)));

            testArchiver.archive(testJar, sources.toArray(new Source[sources.size()]));
          }
        });
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
      projectHelper.attachArtifact(project, "jar", "tests", testJar);
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

  class BuildContextDirectorySource implements Source {
    final List<Entry> entries = new ArrayList<>();

    public BuildContextDirectorySource(Iterable<AggregateInput> inputs) {
      Set<String> paths = new HashSet<>();
      for (AggregateInput input : inputs) {
        File basedir = input.getBasedir();
        for (String relativePath : getRelativePaths(basedir, input.getResource())) {
          if (paths.add(relativePath)) {
            entries.add(new FileEntry(relativePath, new File(basedir, relativePath)));
          }
        }
      }
    }

    private Iterable<String> getRelativePaths(File basedir, File resource) {
      List<String> paths = new ArrayList<>();
      Iterator<Path> names = basedir.toPath().relativize(resource.toPath()).iterator();
      StringBuilder path = new StringBuilder();
      while (names.hasNext()) {
        if (path.length() > 0) {
          path.append('/'); // always use forward slash for path separator
        }
        path.append(names.next().toString());
        paths.add(path.toString());
      }
      return paths;
    }

    @Override
    public Iterable<Entry> entries() {
      return entries;
    }

    @Override
    public boolean isDirectory() {
      return false;
    }

    @Override
    public void close() throws IOException {}

  }
}
