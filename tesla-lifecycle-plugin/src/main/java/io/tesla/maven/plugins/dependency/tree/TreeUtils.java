package io.tesla.maven.plugins.dependency.tree;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;

public class TreeUtils {

  public static DependencyNode node(String coordinate) {
    return new DefaultDependencyNode(new DefaultArtifact(coordinate));
  }

  public static void addChildren(DependencyNode parent, DependencyNode... children) {
    List<DependencyNode> nodes = new ArrayList<DependencyNode>();
    for (DependencyNode n : children) {
      nodes.add(n);
    }
    parent.setChildren(nodes);
  }
}
