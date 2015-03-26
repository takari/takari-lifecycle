package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = DirectReference.GOAL)
public class BasicMojo extends AbstractMojo {

  public void execute() throws MojoExecutionException, MojoFailureException {

  }

}
