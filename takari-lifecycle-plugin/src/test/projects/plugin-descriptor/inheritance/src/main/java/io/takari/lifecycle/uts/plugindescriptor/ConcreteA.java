package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "a")
public class ConcreteA extends ConcreteB {

  // this overrides component in the parent
  @Parameter
  private String parameter;

  public void execute() throws MojoExecutionException, MojoFailureException {}

}
