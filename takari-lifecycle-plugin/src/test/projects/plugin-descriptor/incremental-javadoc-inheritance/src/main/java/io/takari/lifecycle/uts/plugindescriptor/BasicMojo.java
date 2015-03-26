package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "goal")
public class BasicMojo extends AbstractBasicMojo {

  public void execute() throws MojoExecutionException, MojoFailureException {

  }

}
