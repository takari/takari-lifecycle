package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "basic")
public class BasicMojo extends AbstractMojo {

  @Parameter
  private String parameter;

  @Parameter(defaultValue = "${project.artifactId}")
  private String parameterWithConfiguration;

  @Parameter(property = "property", defaultValue = "false")
  private boolean parameterWithPropertyAndDefaultValue;

  @Component
  private ComponentClass component;

  public void execute() throws MojoExecutionException, MojoFailureException {

  }

}
