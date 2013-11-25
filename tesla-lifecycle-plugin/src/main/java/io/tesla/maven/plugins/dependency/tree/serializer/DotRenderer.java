package io.tesla.maven.plugins.dependency.tree.serializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

//
// I need unique edges for graphviz, you cannot tell it to render a tree. If you have nodes with the
// same name and edges pointing at the same
// vertex it joins them. You can't display a dirty tree
//

public class DotRenderer extends AbstractRenderer {

  private File file;

  public DotRenderer(File file) {
    this.file = file;
    try {
      this.out = new PrintStream(new FileOutputStream(file));
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  int z, i = 0;
  private Set<String> children = new HashSet<String>();
  private Set<String> labels = new HashSet<String>();
  private List<DependencyNode> nodes = new ArrayList<DependencyNode>();
  private DependencyNode parent;

  private String currentIndent = "";
  private int indentInt = 2;

  public void render(DependencyNode root) {
    out.println("digraph " + nodeId(root) + " {");
    // out.println("  size=\"8,8\";");
    out.println("  node [color=lightblue2, style=filled];");
    out.println("  rankdir=\"LR\";");
    out.println("  node [shape=record, width=.1, height=.1];");

    root.accept(new Serializer());
    out.println("}");
  }

  private class Serializer implements DependencyVisitor {

    public boolean visitEnter(DependencyNode node) {

      nodes.add(z, node);

      if (z > 0) {
        parent = nodes.get(z - 1);
      }

      if (parent != null) {
        String parentId = nodeId(parent);
        String childId = childId(node);
        //
        // This may not be correct, but to make sure the edges are unique we check the child vertex
        // and
        // making sure it's unique so that the edges are unique so that what we have rendered is a
        // tree
        // and not a graph
        //
        if (children.contains(childId)) {
          childId = childId + i++;
        }
        String edge = parentId + " -> " + childId + ";";
        out.print(currentIndent);
        out.println(edge);
        children.add(childId);

        // tesla_lifecycle_plugin0 [label="tesla-lifecycle-plugin"]
        String parentLabel =
            parent.getArtifact().getArtifactId() + " " + parent.getArtifact().getVersion();
        if (!labels.contains(parentId)) {
          out.print(currentIndent);
          out.println(parentId + "[ label = \"" + parentLabel + "\" ]");
          labels.add(parentLabel);
        }

        // this is wrong!! checking the wrong thing
        // tesla_lifecycle_plugin0 [label="tesla-lifecycle-plugin"]
        String childLabel =
            node.getArtifact().getArtifactId() + " " + node.getArtifact().getVersion();
        if (!labels.contains(childId)) {
          out.print(currentIndent);
          out.println(childId + "[ label = \"" + childLabel + "\" ]");
          labels.add(childLabel);
        }

      }


      if (currentIndent.length() <= 0) {
        currentIndent = "  ";
      } else {
        currentIndent = "  " + currentIndent;
      }

      z++;
      return true;
    }

    public boolean visitLeave(DependencyNode node) {
      if (currentIndent.length() >= indentInt) {
        currentIndent = currentIndent.substring(indentInt, currentIndent.length());
      }

      z--;
      return true;
    }
  }

  private String nodeId(DependencyNode node) {
    if (node == null) return "no parent";
    String nodeId = node.getArtifact().getArtifactId().replace("-", "_").replace(".", "_") + z;
    return nodeId;
  }

  private String childId(DependencyNode node) {
    if (node == null) return "no parent";
    String nodeId =
        node.getArtifact().getArtifactId().replace("-", "_").replace(".", "_") + (z + 1);
    return nodeId;
  }

}
