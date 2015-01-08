package io.takari.maven.plugins.configurator;

import java.util.Collection;

import org.apache.maven.lifecycle.MojoExecutionConfigurator;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class TakariMojoExecutionConfigurator implements MojoExecutionConfigurator {

  @Override
  public void configure(MavenProject project, MojoExecution mojoExecution, boolean allowPluginLevelConfig) {

    String groupId = mojoExecution.getGroupId();
    String artifactId = mojoExecution.getArtifactId();
    Plugin plugin = findPlugin(groupId, artifactId, project.getBuildPlugins());

    if (plugin == null && project.getPluginManagement() != null) {
      plugin = findPlugin(groupId, artifactId, project.getPluginManagement().getPlugins());
    }

    if (plugin != null) {
      PluginExecution pluginExecution = findPluginExecution(mojoExecution.getExecutionId(), plugin.getExecutions());
      Xpp3Dom pomConfiguration = null;
      if (pluginExecution != null) {
        pomConfiguration = (Xpp3Dom) pluginExecution.getConfiguration();
      } else if (allowPluginLevelConfig) {
        pomConfiguration = (Xpp3Dom) plugin.getConfiguration();
      }
      Xpp3Dom mojoConfigurationFromPom = (pomConfiguration != null) ? new Xpp3Dom(pomConfiguration) : null;

      //
      // If we have a configuration that is scoped the by the goal name then extract it. It needs to be an
      // element that matches the mojoExecution.getGoal() and the element must have children.
      //
      if (mojoConfigurationFromPom != null && mojoConfigurationFromPom.getChild(mojoExecution.getGoal()) != null && mojoConfigurationFromPom.getChild(mojoExecution.getGoal()).getChildCount() > 0) {
        mojoConfigurationFromPom = mojoConfigurationFromPom.getChild(mojoExecution.getGoal());
      }
      //
      // There seems to be an issue in Maven where the merging is done with the default configuration from the the plugin.xml as
      // the dominant part of the merge which seems incorrect. One would assume that the configuration from the POM woul dbe
      // the dominant part of the merge.
      //
      mojoConfigurationFromPom = Xpp3Dom.mergeXpp3Dom(mojoConfigurationFromPom, mojoExecution.getConfiguration());
      mojoExecution.setConfiguration(mojoConfigurationFromPom);
    }
  }

  private Plugin findPlugin(String groupId, String artifactId, Collection<Plugin> plugins) {
    for (Plugin plugin : plugins) {
      if (artifactId.equals(plugin.getArtifactId()) && groupId.equals(plugin.getGroupId())) {
        return plugin;
      }
    }
    return null;
  }

  private PluginExecution findPluginExecution(String executionId, Collection<PluginExecution> executions) {
    if (StringUtils.isNotEmpty(executionId)) {
      for (PluginExecution execution : executions) {
        if (executionId.equals(execution.getId())) {
          return execution;
        }
      }
    }
    return null;
  }
}
