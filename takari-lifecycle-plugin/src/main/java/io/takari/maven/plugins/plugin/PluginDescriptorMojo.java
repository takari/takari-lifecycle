package io.takari.maven.plugins.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

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
import io.takari.maven.plugins.plugin.model.io.xpp3.PluginDescriptorXpp3Writer;

@Mojo(name = "plugin-descriptor", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class PluginDescriptorMojo extends TakariLifecycleMojo {

  private static final String PATH_MOJOS_XML = "META-INF/takari/mojos.xml";

  private static final String PATH_PLUGIN_XML = "META-INF/maven/plugin.xml";

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

  // TODO must magically compile mojo configuration
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/annotations")
  private File generatedSourcesDirectory;

  @Component
  private AggregatorBuildContext context;


  @Override
  protected void executeMojo() throws MojoExecutionException {
    try {
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

      final Iterable<File> files = inputSet.addInputs(generatedSourcesDirectory, Collections.singleton("*.mojo.xml"), null);
      inputSet.aggregateIfNecessary(new File(outputDirectory, PATH_PLUGIN_XML), new InputAggregator() {
        @Override
        public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException {
          createPluginXml(output, files, classpathFiles, classpathJars);
        }
      });
      inputSet.aggregateIfNecessary(new File(outputDirectory, PATH_MOJOS_XML), new InputAggregator() {
        @Override
        public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException {
          createMojosXml(output, files);
        }
      });
    } catch (IOException e) {
      throw new MojoExecutionException("Could not create plugin descriptor", e);
    }
  }

  protected void createMojosXml(Output<File> output, Iterable<File> files) throws IOException {
    PluginDescriptor mojos = new PluginDescriptor();
    List<MojoDescriptor> descriptors = new ArrayList<>(loadMojos(files).values());
    Sorting.sortDescriptors(descriptors);
    for (MojoDescriptor descriptor : descriptors) {
      mojos.addMojo(descriptor);
    }
    try (OutputStream out = output.newOutputStream()) {
      new PluginDescriptorXpp3Writer().write(out, mojos);
    }
  }

  protected void createPluginXml(Output<File> output, Iterable<File> inputs, Set<File> classpathFiles, Set<File> classpathJars) throws IOException {
    Map<String, MojoDescriptor> classpathMojos = loadClasspathMojos(classpathFiles, classpathJars);
    Map<String, MojoDescriptor> mojos = loadMojos(inputs);

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

  private Map<String, MojoDescriptor> loadMojos(Iterable<File> inputs) throws IOException {
    Map<String, MojoDescriptor> mojos = new HashMap<>();
    for (File mojoXml : inputs) {
      try (InputStream is = new FileInputStream(mojoXml)) {
        readMojosXml(mojos, is);
      } catch (XmlPullParserException e) {
        throw new IOException(e);
      }
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

  private void readMojosXml(Map<String, MojoDescriptor> mojos, InputStream is) throws XmlPullParserException, IOException {
    PluginDescriptor pluginDescriptor = new PluginDescriptorXpp3Reader().read(is);
    for (MojoDescriptor mojo : pluginDescriptor.getMojos()) {
      mojos.put(mojo.getImplementation(), mojo);
    }
  }

  private void readPluginXml(Map<String, MojoDescriptor> mojos, InputStream is) throws XmlPullParserException, IOException {
    for (MojoDescriptor mojo : LegacyPluginDescriptors.readMojos(is)) {
      mojos.put(mojo.getImplementation(), mojo);
    }
  }

  private PluginDescriptor newPluginDescriptor() {
    String defaultGoalPrefix = org.apache.maven.plugin.descriptor.PluginDescriptor.getGoalPrefixFromArtifactId(artifactId);
    if (goalPrefix == null) {
      goalPrefix = defaultGoalPrefix;
    } else if (!goalPrefix.equals(defaultGoalPrefix)) {
      getLog().warn("\n\nGoal prefix is specified as: '" + goalPrefix + "'. " + "Maven currently expects it to be '" + defaultGoalPrefix + "'.\n");
    }

    PluginDescriptor pluginDescriptor = new PluginDescriptor();
    pluginDescriptor.setGroupId(groupId);
    pluginDescriptor.setArtifactId(artifactId);
    pluginDescriptor.setVersion(version);
    pluginDescriptor.setGoalPrefix(goalPrefix);
    pluginDescriptor.setName(name);
    pluginDescriptor.setDescription(description);
    pluginDescriptor.setInheritedByDefault(true); // see org.apache.maven.plugin.descriptor.PluginDescriptor.inheritedByDefault

    return pluginDescriptor;
  }

}
