package io.tesla.maven.plugins.dependency;

import java.io.File;

import org.sonatype.maven.plugin.Goal;

import io.tesla.maven.plugins.dependency.tree.serializer.DotRenderer;
import io.tesla.maven.plugins.dependency.tree.serializer.TreeRenderer;

@Goal("dot")
public class DotTree extends AbstractTree {

  @Override
  protected TreeRenderer renderer() {
    return new DotRenderer(new File("graph.txt"));
  }
}
