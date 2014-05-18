package io.takari.maven.plugins.configurator;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Stack;

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Test;

public class MojoConfigurationMergerTest {

  private static MojoConfigurationProcessor merger = new MojoConfigurationProcessor();
  private static PluginDescriptorBuilder pluginDescriptorBuilder = new PluginDescriptorBuilder();


  @Test
  public void determineGoalFromMojoImplementation() throws Exception {
    InputStream is = getClass().getResourceAsStream("/META-INF/maven/plugin.xml");
    assertNotNull(is);
    PluginDescriptor pluginDescriptor = pluginDescriptorBuilder.build(new InputStreamReader(is, "UTF-8"));
    String goal = merger.determineGoal("io.takari.maven.plugins.jar.Jar", pluginDescriptor);
    assertEquals("We expect the goal name to be 'jar'", "jar", goal);
  }

  @Test
  public void extractionOfMojoSpecificConfigurationAndMergingwithDefaultMojoConfiguration() throws Exception {
    InputStream is = getClass().getResourceAsStream("/META-INF/maven/plugin.xml");
    assertNotNull(is);
    PluginDescriptor pluginDescriptor = pluginDescriptorBuilder.build(new InputStreamReader(is, "UTF-8"));
    String goal = merger.determineGoal("io.takari.maven.plugins.jar.Jar", pluginDescriptor);
    assertEquals("We expect the goal name to be 'jar'", "jar", goal);
    MojoDescriptor mojoDescriptor = pluginDescriptor.getMojo(goal);
    PlexusConfiguration defaultMojoConfiguration = mojoDescriptor.getMojoConfiguration();
    System.out.println(defaultMojoConfiguration);

    PlexusConfiguration configurationFromMaven = builder("configuration") //
        .es("jar") //
        .es("sourceJar").v("true").ee() //
        .ee() //
        .buildPlexusConfiguration();

    PlexusConfiguration mojoConfiguration = merger.extractAndMerge(goal, configurationFromMaven, defaultMojoConfiguration);

    String xml = mojoConfiguration.toString();
    assertXpathEvaluatesTo("java.io.File", "/configuration/classesDirectory/@implementation", xml);
    assertXpathEvaluatesTo("${project.build.outputDirectory}", "/configuration/classesDirectory/@default-value", xml);
    assertXpathEvaluatesTo("java.util.List", "/configuration/reactorProjects/@implementation", xml);
    assertXpathEvaluatesTo("${reactorProjects}", "/configuration/reactorProjects/@default-value", xml);
    assertXpathEvaluatesTo("true", "/configuration/sourceJar", xml);
  }


  @Test
  public void plexusConfigurationMerging() throws Exception {

    PlexusConfiguration mojoConfigurationFromPom = builder("configuration") //
        .es("sourceJar").v("true").ee() //
        .buildPlexusConfiguration();

    PlexusConfiguration defaultMojoConfiguration = builder("configuration") //
        .entry("classesDirectory", "java.io.File", "${project.build.outputDirectory}") //
        .entry("reactorProjects", "java.util.List", "${reactorProjects}") //
        .entry("souceJar", "boolean", "${sourceJar") //
        .buildPlexusConfiguration();

    PlexusConfiguration mojoConfiguration = merger.mergePlexusConfiguration(mojoConfigurationFromPom, defaultMojoConfiguration);

    String xml = mojoConfiguration.toString();
    assertXpathEvaluatesTo("java.io.File", "/configuration/classesDirectory/@implementation", xml);
    assertXpathEvaluatesTo("${project.build.outputDirectory}", "/configuration/classesDirectory/@default-value", xml);
    assertXpathEvaluatesTo("java.util.List", "/configuration/reactorProjects/@implementation", xml);
    assertXpathEvaluatesTo("${reactorProjects}", "/configuration/reactorProjects/@default-value", xml);
    assertXpathEvaluatesTo("true", "/configuration/sourceJar", xml);
  }

  private Builder builder(String name) {
    return new Builder(name);
  }

  public class Builder {
    private Stack<Xpp3Dom> stack;
    private Xpp3Dom configuration;

    public Builder(String name) {
      stack = new Stack<Xpp3Dom>();
      configuration = new Xpp3Dom(name);
    }

    public Builder entry(String name, String implementation, String defaultValue) {
      return es(name).i(implementation).dv(defaultValue).ee();
    }

    public Builder i(String implementation) {
      a("implementation", implementation);
      return this;
    }

    public Builder dv(String value) {
      a("default-value", value);
      return this;
    }

    public Builder es(String name) {
      Xpp3Dom e = new Xpp3Dom(name);
      configuration.addChild(e);
      stack.push(configuration);
      configuration = e;
      return this;
    }

    public Builder ee() {
      configuration = stack.pop();
      return this;
    }

    public Builder v(String value) {
      configuration.setValue(value);
      return this;
    }

    public Builder a(String name, String value) {
      configuration.setAttribute(name, value);
      return this;
    }

    public Xpp3Dom buildXpp3Dom() {
      if (!stack.empty()) {
        throw new IllegalStateException("You have unclosed elements.");
      }
      return configuration;
    }

    public PlexusConfiguration buildPlexusConfiguration() {
      if (!stack.empty()) {
        throw new IllegalStateException("You have unclosed elements.");
      }
      return new XmlPlexusConfiguration(configuration);
    }
  }
}
