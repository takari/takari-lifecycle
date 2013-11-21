package io.tesla.maven.plugins.dependency;

import org.sonatype.maven.plugin.Goal;

import io.tesla.maven.plugins.dependency.tree.serializer.TextRenderer;
import io.tesla.maven.plugins.dependency.tree.serializer.TreeRenderer;

@Goal("text")
public class TextTree extends AbstractTree {

  @Override
  protected TreeRenderer renderer() {
    return new TextRenderer();
  }
}
