package io.takari.maven.plugins.dependency;

import io.takari.maven.plugins.dependency.tree.serializer.TextRenderer;
import io.takari.maven.plugins.dependency.tree.serializer.TreeRenderer;

import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "text")
public class TextTree extends AbstractTree {

  @Override
  protected TreeRenderer renderer() {
    return new TextRenderer();
  }
}
