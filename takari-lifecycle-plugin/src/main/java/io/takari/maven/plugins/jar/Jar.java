/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import static io.takari.maven.plugins.TakariLifecycles.isJarProducingTakariLifecycle;
import static java.util.Arrays.asList;

import ca.vanzyl.provisio.archive.Archiver;
import ca.vanzyl.provisio.archive.EntryContents;
import ca.vanzyl.provisio.archive.EntryOrder;
import ca.vanzyl.provisio.archive.ReproducibilityPolicy;
import ca.vanzyl.provisio.archive.SourceEntry;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputAggregator;
import io.takari.incrementalbuild.aggregator.InputSet;
import io.takari.maven.plugins.TakariLifecycleMojo;
import io.takari.maven.plugins.util.PropertiesWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

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

        try {
            Files.createDirectories(outputDirectory.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        if (mainJar) {
            File jar = outputDirectory
                    .toPath()
                    .resolve(String.format("%s.jar", finalName))
                    .toFile();
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

                        List<Iterable<SourceEntry>> sources = new ArrayList<>();
                        if (archive != null && archive.getManifestFile() != null) {
                            sources.add(jarManifestSource(archive.getManifestFile()));
                        }
                        sources.add(inputsSource(classesDirectory, inputs));
                        sources.add(Set.of(pomPropertiesSource(project)));
                        sources.add(jarManifestSource(project));
                        archive(output.getResource(), sources);
                    }
                });
                if (!processingRequired) {
                    logger.info("Main JAR is up-to-date");
                }

                if (!isJarProducingTakariLifecycle(project.getPackaging())
                        && alternateLifecycleProvidingPrimaryArtifact()) {
                    // We have a non-Takari lifecycle and a property in the MavenSession has been explicity set to
                    // indicate the lifecycle
                    // executing will produce the primary artifact.
                    projectHelper.attachArtifact(project, "jar", jar);
                    logger.info(
                            "The '{}' lifecycle has indicated it will produce the primary artifact. Attaching JAR as a secondary artifact.",
                            project.getPackaging());
                } else {
                    // We have a standard Takari lifecycle so set the JAR created here as the primary artifact.
                    project.getArtifact().setFile(jar);
                }
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

        if (sourceJar) {
            final Map<File, List<File>> sources = new LinkedHashMap<>();
            File sourceJar = outputDirectory
                    .toPath()
                    .resolve(String.format("%s-%s.jar", finalName, "sources"))
                    .toFile();
            InputSet registeredOutput = buildContext.newInputSet();
            try {
                for (String sourceRoot : project.getCompileSourceRoots()) {
                    File dir = Path.of(sourceRoot).toFile();
                    if (dir.isDirectory()) {
                        dir = dir.getCanonicalFile();
                        List<File> inputs = StreamSupport.stream(
                                        registeredOutput
                                                .addInputs(dir, null, null)
                                                .spliterator(),
                                        false)
                                .collect(Collectors.toList());
                        logger.debug("Analyzing sources directory {} with {} entries", dir, inputs.size());
                        sources.computeIfAbsent(dir, k -> new ArrayList<>()).addAll(inputs);
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
            File testJar = outputDirectory
                    .toPath()
                    .resolve(String.format("%s-%s.jar", finalName, "tests"))
                    .toFile();
            InputSet registeredOutput = buildContext.newInputSet();
            try {
                testClassesDirectory = testClassesDirectory.getCanonicalFile();
                Iterable<File> inputs = registeredOutput.addInputs(testClassesDirectory, null, null);
                logger.debug("Analyzing test classes directory {} with {} entries", testClassesDirectory, size(inputs));
                boolean processingRequired = registeredOutput.aggregateIfNecessary(testJar, new InputAggregator() {
                    @Override
                    public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException {
                        logger.info("Building test JAR.");

                        archive(
                                output.getResource(),
                                asList(inputsSource(testClassesDirectory, inputs), jarManifestSource(project)));
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

    private void archive(File jar, List<Iterable<SourceEntry>> sources) throws IOException {
        // NORMALIZED + NAME is the archiver 2.x equivalent of 1.x normalize(true): fixed timestamps, name-sorted
        // entries
        Archiver archiver = Archiver.builder() //
                .reproducibility(ReproducibilityPolicy.NORMALIZED) //
                .entryOrder(EntryOrder.NAME) //
                .build();
        archiver.archive(jar.toPath(), new AggregateSource(sources));
        if (logger.isDebugEnabled()) {
            int size = 0;
            for (Iterable<?> source : sources) {
                size += size(source);
            }
            logger.debug("Created archive {} with {} entries", jar, size);
        }
    }

    static String getRelativePath(File basedir, File resource) {
        return basedir.toPath()
                .relativize(resource.toPath())
                .toString()
                .replace('\\', '/'); // always use forward slash for path separator
    }

    private List<SourceEntry> inputsSource(Map<File, List<File>> inputs) throws IOException {
        final List<SourceEntry> entries = new ArrayList<>();
        for (File basedir : inputs.keySet()) {
            entries.addAll(inputsSource(basedir, inputs.get(basedir)));
        }
        return entries;
    }

    private List<SourceEntry> inputsSource(File basedir, Iterable<File> inputs) throws IOException {
        final List<SourceEntry> entries = new ArrayList<>();
        for (File input : inputs) {
            entries.add(fileEntry(getRelativePath(basedir, input), input));
        }
        return entries;
    }

    public static Iterable<SourceEntry> jarManifestSource(File file) throws IOException {
        return Set.of(fileEntry(MANIFEST_PATH, file));
    }

    // mode and time are left unset (-1): the NORMALIZED policy assigns fixed timestamps and 0644
    private static SourceEntry fileEntry(String entryName, File file) throws IOException {
        return SourceEntry.file(entryName, EntryContents.of(file.toPath()), -1, -1);
    }

    private static SourceEntry bytesEntry(String entryName, byte[] contents) {
        return SourceEntry.file(entryName, EntryContents.of(contents), -1, -1);
    }

    private Iterable<SourceEntry> jarManifestSource(MavenProject project) throws IOException {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.putValue("Manifest-Version", "1.0");
        main.putValue("Archiver-Version", "Provisio Archiver");
        main.putValue("Created-By", "Takari Inc.");
        main.putValue("Specification-Title", project.getArtifactId());
        main.putValue("Specification-Version", project.getVersion());
        main.putValue("Implementation-Title", project.getArtifactId());
        main.putValue("Implementation-Version", project.getVersion());
        main.putValue("Implementation-Vendor-Id", project.getGroupId());
        main.putValue("Build-Jdk-Spec", System.getProperty("java.specification.version", "unknown"));

        if (archive != null && archive.getManifestEntries() != null) {
            for (Map.Entry<String, String> extra : archive.getManifestEntries().entrySet()) {
                if (extra.getValue() != null) {
                    main.putValue(extra.getKey(), extra.getValue());
                }
            }
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        manifest.write(buf);

        return Set.of(bytesEntry(MANIFEST_PATH, buf.toByteArray()));
    }

    protected SourceEntry pomPropertiesSource(MavenProject project) throws IOException {
        String entryName =
                String.format("META-INF/maven/%s/%s/pom.properties", project.getGroupId(), project.getArtifactId());

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Properties properties = new Properties();
        properties.setProperty("groupId", project.getGroupId());
        properties.setProperty("artifactId", project.getArtifactId());
        properties.setProperty("version", project.getVersion());
        PropertiesWriter.write(properties, null, buf);

        return bytesEntry(entryName, buf.toByteArray());
    }

    private static int size(final Iterable<?> iterable) {
        if (iterable instanceof Collection) {
            return ((Collection<?>) iterable).size();
        }
        int size = 0;
        for (Object o : iterable) {
            size++;
        }
        return size;
    }
}
