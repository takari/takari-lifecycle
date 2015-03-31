package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "concrete")
public class ConcreteMojo extends AbstractLocalMojo {

  @Parameter
  private String parameter;

  @Component
  private ComponentClass component;

  public void execute() throws MojoExecutionException, MojoFailureException {

  }

}
