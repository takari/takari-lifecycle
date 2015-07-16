package io.takari.maven.plugins.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.codehaus.plexus.component.repository.ComponentRequirement;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.google.common.base.Throwables;

import io.takari.maven.plugins.plugin.model.MojoDescriptor;
import io.takari.maven.plugins.plugin.model.MojoParameter;
import io.takari.maven.plugins.plugin.model.MojoRequirement;

class LegacyPluginDescriptors {

  public static Collection<MojoDescriptor> readMojos(InputStream is) throws IOException, XmlPullParserException {
    Reader reader = ReaderFactory.newXmlReader(is);
    org.apache.maven.plugin.descriptor.PluginDescriptor pluginDescriptor;
    try {
      pluginDescriptor = new PluginDescriptorBuilder().build(reader);
    } catch (PlexusConfigurationException e) {
      Throwables.propagateIfPossible(e.getCause(), IOException.class, XmlPullParserException.class);
      throw Throwables.propagate(e);
    }
    List<MojoDescriptor> result = new ArrayList<>();
    for (org.apache.maven.plugin.descriptor.MojoDescriptor mojo : pluginDescriptor.getMojos()) {
      result.add(toMojoDescriptor(mojo));
    }
    return result;
  }

  private static MojoDescriptor toMojoDescriptor(org.apache.maven.plugin.descriptor.MojoDescriptor mojo) {
    MojoDescriptor result = new MojoDescriptor();

    result.setGoal(mojo.getGoal());
    result.setDescription(mojo.getDescription());
    result.setSince(mojo.getSince());
    result.setRequiresDependencyResolution(mojo.getDependencyResolutionRequired());
    result.setRequiresDependencyCollection(mojo.getDependencyCollectionRequired());
    result.setRequiresDirectInvocation(mojo.isDirectInvocationOnly());
    result.setRequiresProject(mojo.isProjectRequired());
    result.setRequiresReports(mojo.isRequiresReports());
    result.setAggregator(mojo.isAggregator());
    result.setRequiresOnline(mojo.isOnlineRequired());
    result.setInheritedByDefault(mojo.isInheritedByDefault());
    result.setPhase(mojo.getPhase());
    result.setImplementation(mojo.getImplementation());
    result.setLanguage(mojo.getLanguage());
    result.setConfigurator(mojo.getComponentConfigurator());
    result.setInstantiationStrategy(mojo.getInstantiationStrategy());
    result.setExecutionStrategy(mojo.getExecutionStrategy());
    result.setThreadSafe(mojo.isThreadSafe());
    result.setDeprecated(mojo.getDeprecated());

    List<MojoParameter> parameters = new ArrayList<>();
    if (mojo.getParameters() != null) {
      for (Parameter parameter : mojo.getParameters()) {
        parameters.add(toMojoParameter(parameter));
      }
    }
    result.setParameters(parameters);

    List<MojoRequirement> requirements = new ArrayList<>();
    for (ComponentRequirement requirement : mojo.getRequirements()) {
      requirements.add(toMojoRequirement(requirement));
    }
    result.setRequirements(requirements);

    return result;
  }

  private static MojoRequirement toMojoRequirement(ComponentRequirement requirement) {
    MojoRequirement result = new MojoRequirement();

    result.setFieldName(requirement.getFieldName());
    result.setRole(requirement.getRole());
    result.setRoleHint(requirement.getRoleHint());

    return result;
  }

  private static MojoParameter toMojoParameter(Parameter parameter) {
    MojoParameter result = new MojoParameter();

    result.setName(parameter.getName());
    result.setAlias(parameter.getAlias());
    result.setType(parameter.getType());
    result.setRequired(parameter.isRequired());
    result.setEditable(parameter.isEditable());
    result.setDescription(parameter.getDescription());
    result.setDeprecated(parameter.getDeprecated());
    result.setSince(parameter.getSince());
    result.setImplementation(parameter.getImplementation());
    result.setDefaultValue(parameter.getDefaultValue());
    result.setExpression(parameter.getExpression());

    return result;
  }



}
