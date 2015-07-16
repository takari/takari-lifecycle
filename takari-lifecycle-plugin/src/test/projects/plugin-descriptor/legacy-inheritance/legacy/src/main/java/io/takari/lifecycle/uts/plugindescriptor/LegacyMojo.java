package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "legacy")
public class LegacyMojo extends AbstractMojo {

  /** inherited legacy parameter */
  @Parameter(defaultValue = "default-value")
  private String inheritedLegacyParameter;

  public void execute() throws MojoExecutionException, MojoFailureException {}

}
