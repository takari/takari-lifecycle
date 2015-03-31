package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

abstract class Abstract extends AbstractMojo {
  @Parameter
  private String parameter;

  @Component
  private ComponentClass component;

}
