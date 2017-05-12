package io.takari.lifecycle.uts.plugindescriptor;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.takari.builder.internal.maven.AbstractIncrementalMojo;

@Mojo(name = "basic-builder")
public class BasicBuilder extends AbstractIncrementalMojo {
  
  protected BasicBuilder(Class<?> builderType) {
    super(builderType);
  }

}
