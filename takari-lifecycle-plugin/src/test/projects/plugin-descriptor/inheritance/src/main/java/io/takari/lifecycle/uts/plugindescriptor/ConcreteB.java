package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "b")
public class ConcreteB extends Abstract {

  // this overrides parameter in the parent
  @Component
  private Object parameter;

  public void execute() throws MojoExecutionException, MojoFailureException {}

}
