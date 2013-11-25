package io.tesla.maven.plugins.dependency;

import io.tesla.maven.plugins.dependency.tree.serializer.TextRenderer;
import io.tesla.maven.plugins.dependency.tree.serializer.TreeRenderer;

import org.sonatype.maven.plugin.Goal;

@Goal("text")
public class TextTree extends AbstractTree {

  @Override
  protected TreeRenderer renderer() {
    return new TextRenderer();
  }
}
