package io.takari.maven.plugins.configurator;

import java.util.List;

import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.configurator.AbstractComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.converters.special.ClassRealmConverter;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

import com.google.common.collect.ImmutableList;

public class TakariMojoConfigurator extends AbstractComponentConfigurator {

  private List<String> commonConfigurationElements;

  public TakariMojoConfigurator() {
    commonConfigurationElements =
        ImmutableList.of("mojoDescriptor", "outputDirectory", "project", "properties",
            "reactorProjects", "remoteRepositories", "repositorySystemSession", "settings", "skip");
  }

  @Override
  public void configureComponent(final Object component, final PlexusConfiguration configuration,
      final ExpressionEvaluator evaluator, final ClassRealm realm,
      final ConfigurationListener listener) throws ComponentConfigurationException {
    converterLookup.registerConverter(new ClassRealmConverter(realm));

    String goal = determineGoal(component);

    //
    // I need to be able to distinguish between common configuration from the super-class
    // TakariLifecycleMojo and the specific configuration for the Mojo being executed.
    //
    PlexusConfiguration mojoConfiguration = new XmlPlexusConfiguration("configuration");
    for (PlexusConfiguration element : configuration.getChildren()) {
      if (isCommon(element)) {
        mojoConfiguration.addChild(element);
      } else if (element.getName().equals(goal)) {
        for (PlexusConfiguration goalConfigurationElements : element.getChildren()) {
          mojoConfiguration.addChild(goalConfigurationElements);
        }
      }
    }

    System.out.println(mojoConfiguration);

    new ObjectWithFieldsConverter().processConfiguration(converterLookup, component, realm,
        mojoConfiguration, evaluator, listener);
  }

  private boolean isCommon(PlexusConfiguration configuration) {
    return commonConfigurationElements.contains(configuration.getName());
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
}
