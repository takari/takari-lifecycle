package io.takari.maven.plugins.dependency.tree.serializer;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

public class TextRenderer extends AbstractRenderer {

  private String currentIndent = "";

  public void render(DependencyNode root) {
    root.accept(new Serializer());
  }

  private class Serializer implements DependencyVisitor {

    public boolean visitEnter(DependencyNode node) {
      out.println(currentIndent + node);
      if (currentIndent.length() <= 0) {
        currentIndent = "   ";
      } else {
        currentIndent = "   " + currentIndent;
      }
      return true;
    }

    public boolean visitLeave(DependencyNode node) {
      currentIndent = currentIndent.substring(3, currentIndent.length());
      return true;
    }
  }
}
