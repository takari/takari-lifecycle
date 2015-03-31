package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractLocalMojo extends AbstractExternalMojo {

  /** inherited parameter */
  @Parameter
  private String inheritedParameter;

  @Component
  private ComponentClass inheritedComponent;

}
