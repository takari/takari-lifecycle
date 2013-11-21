package io.tesla.maven.plugins.dependency.tree.serializer;

import java.io.PrintStream;

public abstract class AbstractRenderer implements TreeRenderer {

  protected PrintStream out;

  public AbstractRenderer() {
    this(null);
  }

  public AbstractRenderer(PrintStream out) {
    this.out = (out != null) ? out : System.out;
  }  
}
