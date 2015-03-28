/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.configurator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;

//
// The normal process in Maven does merging of default Mojo configuration with the configuration
// provided by the POM, but we need to change the procesing in order to support the namespacing of configuration in the POM
// configuraiton. So we take the original configuration, take the POM configuration and take out the configuration that is namespaced to
// the Mojo we are executing and create a configuration that looks normal to the standard Maven process.
//
public class MojoConfigurationProcessor {

  private static PluginDescriptorBuilder pluginDescriptorBuilder = new PluginDescriptorBuilder();

  public PlexusConfiguration mojoConfigurationFor(Object mojoInstance, PlexusConfiguration pluginConfigurationFromMaven) throws ComponentConfigurationException {
    try (InputStream is = mojoInstance.getClass().getResourceAsStream("/META-INF/maven/plugin.xml")) {
      PluginDescriptor pd = pluginDescriptorBuilder.build(new InputStreamReader(is, "UTF-8")); // closes input stream too
      String goal = determineGoal(mojoInstance.getClass().getName(), pd);
      PlexusConfiguration defaultMojoConfiguration = pd.getMojo(goal).getMojoConfiguration();
      PlexusConfiguration mojoConfiguration = extractAndMerge(goal, pluginConfigurationFromMaven, defaultMojoConfiguration);
      return mojoConfiguration;
    } catch (Exception e) {
      throw new ComponentConfigurationException(e);
    }
  }

  /**
   * Extract the Mojo specific configuration from the incoming plugin configuration from Maven by looking at an enclosing element with the goal name. Use this and merge it with the default Mojo
   * configuration and use this to apply values to the Mojo.
   * 
   * @param goal
   * @param pluginConfigurationFromMaven
   * @param defaultMojoConfiguration
   * @return
   * @throws ComponentConfigurationException
   */
  PlexusConfiguration extractAndMerge(String goal, PlexusConfiguration pluginConfigurationFromMaven, PlexusConfiguration defaultMojoConfiguration) throws ComponentConfigurationException {
    //
    // We need to extract the specific configuration for this goal out of the POM configuration
    //
    PlexusConfiguration mojoConfigurationFromPom = new XmlPlexusConfiguration("configuration");
    for (PlexusConfiguration element : pluginConfigurationFromMaven.getChildren()) {
      if (element.getName().equals(goal)) {
        for (PlexusConfiguration goalConfigurationElements : element.getChildren()) {
          mojoConfigurationFromPom.addChild(goalConfigurationElements);
        }
      }
    }
    return new XmlPlexusConfiguration(Xpp3Dom.mergeXpp3Dom(convert(mojoConfigurationFromPom), convert(defaultMojoConfiguration)));
  }

  PlexusConfiguration mergePlexusConfiguration(PlexusConfiguration dominant, PlexusConfiguration recessive) throws ComponentConfigurationException {
    return new XmlPlexusConfiguration(Xpp3Dom.mergeXpp3Dom(convert(dominant), convert(recessive)));
  }

  Xpp3Dom convert(PlexusConfiguration c) throws ComponentConfigurationException {
    try {
      return Xpp3DomBuilder.build(new StringReader(c.toString()));
    } catch (Exception e) {
      throw new ComponentConfigurationException("Failure converting PlexusConfiguration to Xpp3Dom.", e);
    }
  }

  String determineGoal(String className, PluginDescriptor pluginDescriptor) throws ComponentConfigurationException {
    List<MojoDescriptor> mojos = pluginDescriptor.getMojos();
    for (MojoDescriptor mojo : mojos) {
      if (className.equals(mojo.getImplementation())) {
        return mojo.getGoal();
      }
    }
    throw new ComponentConfigurationException("Cannot find the goal implementation with " + className);
  }
}
