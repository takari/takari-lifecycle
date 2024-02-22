/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.plugin;

import static io.takari.incrementalbuild.Incremental.Configuration.ignore;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputAggregator;
import io.takari.incrementalbuild.aggregator.InputSet;
import io.takari.maven.plugins.TakariLifecycleMojo;
import io.takari.maven.plugins.plugin.model.MojoDescriptor;
import io.takari.maven.plugins.plugin.model.MojoParameter;
import io.takari.maven.plugins.plugin.model.MojoRequirement;
import io.takari.maven.plugins.plugin.model.PluginDescriptor;
import io.takari.maven.plugins.plugin.model.io.xpp3.PluginDescriptorXpp3Reader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

@Mojo(
        name = "plugin-descriptor",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class PluginDescriptorMojo extends TakariLifecycleMojo {

    static final String PATH_MOJOS_XML = "META-INF/takari/mojos.xml";

    private static final String PATH_PLUGIN_XML = "META-INF/maven/plugin.xml";

    private static final String PATH_METADATA_XML = "META-INF/m2e/lifecycle-mapping-metadata.xml";

    @Parameter(defaultValue = "${project.groupId}", readonly = true)
    private String groupId;

    @Parameter(defaultValue = "${project.artifactId}", readonly = true)
    private String artifactId;

    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String version;

    @Parameter(defaultValue = "${project.name}", readonly = true)
    private String name;

    @Parameter(defaultValue = "${project.description}", readonly = true)
    private String description;

    /**
     * The goal prefix that will appear before the ":".
     */
    @Parameter
    private String goalPrefix;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.compileArtifacts}", readonly = true)
    private List<Artifact> dependencies;

    /**
     * Hand-written m2e lifecycle mapping xml file location.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/m2e/lifecycle-mapping-metadata.xml")
    private File eclipseMetadataFile;

    // TODO introduce Resource digester in io.takari.incrementalbuild.maven.internal.digest.Digesters
    @Parameter(defaultValue = "${project.build.resources}", readonly = true)
    @Incremental(configuration = ignore)
    private List<Resource> resources;

    @Component
    private AggregatorBuildContext context;

    @Override
    protected void executeMojo() throws MojoExecutionException {

        try {
            File mojosXml = new File(outputDirectory, PATH_MOJOS_XML);
            if (!mojosXml.isFile()) {
                // the project does not have any mojos, don't create empty plugin.xml
                return;
            }

            InputSet inputSet = context.newInputSet();

            final Set<File> classpathJars = new LinkedHashSet<>();
            final Set<File> classpathFiles = new LinkedHashSet<>();
            for (Artifact artifact : dependencies) {
                if (artifact.getFile().isFile()) {
                    classpathJars.add(inputSet.addInput(artifact.getFile()));
                } else if (artifact.getFile().isDirectory()) {
                    File file = new File(artifact.getFile(), PATH_MOJOS_XML);
                    if (file.canRead()) {
                        classpathFiles.add(inputSet.addInput(file));
                    }
                }
            }
            inputSet.addInput(new File(outputDirectory, PATH_MOJOS_XML));

            if (eclipseMetadataFile.isFile()) {
                inputSet.addInput(eclipseMetadataFile);
            }

            // fail the build if m2e lifecycle mapping metadata is found among project <resources>
            // the metadata will be overwritten by #createEclipseMetadataXml, which almost certainly not what the user
            // expects
            for (Resource resource : resources) {
                File otherEclipseMetadataFile = new File(resource.getDirectory(), PATH_METADATA_XML);
                if (otherEclipseMetadataFile.isFile()) {
                    throw new MojoExecutionException(String.format(
                            "Unexpected Eclipse m2e metadata found %s. Did you mean to move it to %s",
                            otherEclipseMetadataFile, eclipseMetadataFile));
                }
            }

            inputSet.aggregateIfNecessary(new File(outputDirectory, PATH_PLUGIN_XML), new InputAggregator() {
                @Override
                public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException {
                    createPluginXml(output, mojosXml, classpathFiles, classpathJars);
                }
            });
            inputSet.aggregateIfNecessary(new File(outputDirectory, PATH_METADATA_XML), new InputAggregator() {
                @Override
                public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException {
                    createEclipseMetadataXml(output, mojosXml, eclipseMetadataFile);
                }
            });

        } catch (IOException e) {
            throw new MojoExecutionException("Could not create plugin descriptor", e);
        }
    }

    protected void createPluginXml(Output<File> output, File input, Set<File> classpathFiles, Set<File> classpathJars)
            throws IOException {
        Map<String, MojoDescriptor> classpathMojos = loadClasspathMojos(classpathFiles, classpathJars);
        Map<String, MojoDescriptor> mojos = loadMojos(input);

        PluginDescriptor plugin = newPluginDescriptor();

        for (MojoDescriptor gleaned : mojos.values()) {
            MojoDescriptor descriptor = gleaned.clone();

            if (descriptor.getGoal() == null) {
                continue; // abstract mojo, skip
            }

            for (String parent : descriptor.getSuperclasses()) {
                MojoDescriptor inherited = mojos.get(parent);
                if (inherited == null) {
                    inherited = classpathMojos.get(parent);
                }
                if (inherited != null) {
                    for (MojoParameter parameter : inherited.getParameters()) {
                        if (!containsField(descriptor, parameter.getName())) {
                            descriptor.addParameter(parameter.clone());
                        }
                    }
                    for (MojoRequirement requirement : inherited.getRequirements()) {
                        if (!containsField(descriptor, requirement.getFieldName())) {
                            descriptor.addRequirement(requirement.clone());
                        }
                    }
                }
            }
            plugin.addMojo(descriptor);
        }

        try (OutputStream out = output.newOutputStream()) {
            new PluginDescriptorWriter().writeDescriptor(out, plugin);
        }
    }

    private boolean containsField(MojoDescriptor descriptor, String fieldName) {
        for (MojoParameter parameter : descriptor.getParameters()) {
            if (fieldName.equals(parameter.getName())) {
                return true;
            }
        }
        for (MojoRequirement requirement : descriptor.getRequirements()) {
            if (fieldName.equals(requirement.getFieldName())) {
                return true;
            }
        }
        return false;
    }

    private Map<String, MojoDescriptor> loadMojos(File mojosXml) throws IOException {
        Map<String, MojoDescriptor> mojos = new HashMap<>();
        try (InputStream is = new FileInputStream(mojosXml)) {
            readMojosXml(mojos, is);
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        }
        return mojos;
    }

    private Map<String, MojoDescriptor> loadClasspathMojos(Set<File> files, Set<File> jars) {
        Map<String, MojoDescriptor> mojos = new HashMap<>();
        for (File jarFile : jars) {
            try (ZipFile zip = new ZipFile(jarFile)) {
                ZipEntry entry = zip.getEntry(PATH_MOJOS_XML);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        readMojosXml(mojos, is);
                    }
                    continue;
                }
                entry = zip.getEntry(PATH_PLUGIN_XML);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        readPluginXml(mojos, is);
                    }
                    continue;
                }
            } catch (XmlPullParserException | IOException e) {
                logger.warn("Could not read dependency mojos.xml " + jarFile, e);
            }
        }
        for (File mojosXmlFile : files) {
            try (InputStream is = new FileInputStream(mojosXmlFile)) {
                readMojosXml(mojos, is);
            } catch (XmlPullParserException | IOException e) {
                logger.warn("Could not read dependency mojos.xml " + mojosXmlFile, e);
            }
        }

        return mojos;
    }

    private void readMojosXml(Map<String, MojoDescriptor> mojos, InputStream is)
            throws XmlPullParserException, IOException {
        PluginDescriptor pluginDescriptor = new PluginDescriptorXpp3Reader().read(is);
        for (MojoDescriptor mojo : pluginDescriptor.getMojos()) {
            mojos.put(mojo.getImplementation(), mojo);
        }
    }

    private void readPluginXml(Map<String, MojoDescriptor> mojos, InputStream is)
            throws XmlPullParserException, IOException {
        for (MojoDescriptor mojo : LegacyPluginDescriptors.readMojos(is)) {
            mojos.put(mojo.getImplementation(), mojo);
        }
    }

    private PluginDescriptor newPluginDescriptor() {
        String defaultGoalPrefix =
                org.apache.maven.plugin.descriptor.PluginDescriptor.getGoalPrefixFromArtifactId(artifactId);
        if (goalPrefix == null) {
            goalPrefix = defaultGoalPrefix;
        } else if (!goalPrefix.equals(defaultGoalPrefix)) {
            getLog().warn("\n\nGoal prefix is specified as: '" + goalPrefix + "'. "
                    + "Maven currently expects it to be '" + defaultGoalPrefix + "'.\n");
        }

        PluginDescriptor pluginDescriptor = new PluginDescriptor();
        pluginDescriptor.setGroupId(groupId);
        pluginDescriptor.setArtifactId(artifactId);
        pluginDescriptor.setVersion(version);
        pluginDescriptor.setGoalPrefix(goalPrefix);
        pluginDescriptor.setName(name);
        pluginDescriptor.setDescription(description);
        pluginDescriptor.setInheritedByDefault(
                true); // see org.apache.maven.plugin.descriptor.PluginDescriptor.inheritedByDefault

        return pluginDescriptor;
    }

    protected void createEclipseMetadataXml(Output<File> output, File mojosXml, File existingEclipseMetadataXml)
            throws IOException {
        try (OutputStream out = output.newOutputStream()) {
            if (existingEclipseMetadataXml.isFile()) {
                try (InputStream in = Files.newInputStream(existingEclipseMetadataXml.toPath())) {
                    in.transferTo(out);
                }
            } else {
                Map<String, MojoDescriptor> mojos = loadMojos(mojosXml);
                List<String> goals = mojos.values().stream()
                        .filter(MojoDescriptor::isTakariBuilder)
                        .map(MojoDescriptor::getGoal)
                        .collect(Collectors.toList());

                writeEclipseMetadataXml(out, goals);
            }
        }
    }

    private void writeEclipseMetadataXml(OutputStream out, List<String> goals) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
        XMLWriter w = new PrettyPrintXMLWriter(writer, "UTF-8", null);

        w.writeMarkup("\n<!-- Generated by " + this.getClass().getSimpleName() + " -->\n\n");

        w.startElement("lifecycleMappingMetadata");
        if (!goals.isEmpty()) {
            w.startElement("pluginExecutions");
            w.startElement("pluginExecution");
            w.startElement("pluginExecutionFilter");
            w.startElement("goals");
            goals.forEach(g -> writeGoalElement(w, g));
            w.endElement(); // goals
            w.endElement(); // pluginExecutionFilter
            w.startElement("action");
            w.startElement("configurator");
            w.startElement("id");
            w.writeText("io.takari.m2e.incrementalbuild.builderMojoExecutionConfigurator");
            w.endElement(); // id
            w.endElement(); // configurator
            w.endElement(); // action
            w.endElement(); // pluginExecution
            w.endElement(); // pluginExecutions
        }
        w.endElement(); // lifecycleMappingMetadata

        writer.close();
    }

    private void writeGoalElement(XMLWriter w, String goal) {
        w.startElement("goal");
        w.writeText(goal);
        w.endElement();
    }
}
