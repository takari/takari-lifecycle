package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

abstract class AbstractBasicMojo extends AbstractMojo {

  /**
   * Documentation
   */
  @Parameter
  private String parameter;

}
