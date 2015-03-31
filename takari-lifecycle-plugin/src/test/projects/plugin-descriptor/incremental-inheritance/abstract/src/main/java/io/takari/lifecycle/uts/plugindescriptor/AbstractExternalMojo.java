package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractExternalMojo extends AbstractMojo {

  /** inherited external parameter */
  @Parameter
  private String inheritedExternalParameter;

  @Component
  private ComponentClass inheritedExternalComponent;

}
