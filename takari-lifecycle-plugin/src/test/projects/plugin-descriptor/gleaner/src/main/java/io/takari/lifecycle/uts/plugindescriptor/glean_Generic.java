package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugins.annotations.Parameter;

public abstract class glean_Generic<T> {

  @Parameter
  private T genericParameter;
}
