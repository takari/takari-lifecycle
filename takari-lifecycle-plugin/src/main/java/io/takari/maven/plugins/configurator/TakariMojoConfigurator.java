package io.takari.maven.plugins.configurator;

import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.configurator.AbstractComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.converters.special.ClassRealmConverter;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class TakariMojoConfigurator extends AbstractComponentConfigurator {

  private static PluginDescriptorBuilder pluginDescriptorBuilder = new PluginDescriptorBuilder();

  @Override
  public void configureComponent(final Object component, //
      final PlexusConfiguration configuration, //
      final ExpressionEvaluator evaluator, //
      final ClassRealm realm, //
      final ConfigurationListener listener) throws ComponentConfigurationException {
    converterLookup.registerConverter(new ClassRealmConverter(realm));
    InputStream is = component.getClass().getResourceAsStream("/META-INF/maven/plugin.xml");
    try {
      PluginDescriptor pd = pluginDescriptorBuilder.build(new InputStreamReader(is, "UTF-8"));
      String goal = determineGoal(component);
      PlexusConfiguration mojoConfigurationFromPom = new XmlPlexusConfiguration("configuration");
      for (PlexusConfiguration element : configuration.getChildren()) {
        if (element.getName().equals(goal)) {
          for (PlexusConfiguration goalConfigurationElements : element.getChildren()) {
            mojoConfigurationFromPom.addChild(goalConfigurationElements);
          }
        }
      }
      PlexusConfiguration defaultMojoConfiguration = pd.getMojo(goal).getMojoConfiguration();
      PlexusConfiguration mojoConfiguration = new XmlPlexusConfiguration(Xpp3Dom.mergeXpp3Dom(convert(mojoConfigurationFromPom), convert(defaultMojoConfiguration)));
      new ObjectWithFieldsConverter().processConfiguration(converterLookup, component, realm, mojoConfiguration, evaluator, listener);
    } catch (Exception e) {
      throw new ComponentConfigurationException(e);
    }

  }

  private String determineGoal(Object component) {
    String goal;
    String className = component.getClass().getName();
    if (className.endsWith("Jar")) {
      goal = "jar";
    } else if (className.endsWith("")) {
      goal = "process-resources";
    } else {
      goal = null;
    }
    return goal;
  }

  public static Xpp3Dom convert(PlexusConfiguration c) {
    Xpp3Dom dom = new Xpp3Dom("configuration");
    PlexusConfiguration[] ces = c.getChildren();
    if (ces != null) {
      for (PlexusConfiguration ce : ces) {
        String value = ce.getValue(null);
        String defaultValue = ce.getAttribute("default-value", null);
        if (value != null || defaultValue != null) {
          Xpp3Dom e = new Xpp3Dom(ce.getName());
          e.setValue(value);
          if (defaultValue != null) {
            e.setAttribute("default-value", defaultValue);
          }
          dom.addChild(e);
        }
      }
    }
    return dom;
  }
}
