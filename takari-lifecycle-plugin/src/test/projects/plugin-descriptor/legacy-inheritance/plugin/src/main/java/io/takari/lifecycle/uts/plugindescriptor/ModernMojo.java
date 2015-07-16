package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "modern")
public class ModernMojo extends LegacyMojo {

  /** parameter */
  @Parameter
  private String parameter;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    super.execute();
  }
}
