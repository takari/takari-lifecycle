package io.takari.maven.plugins.dependency;

import io.takari.maven.plugins.dependency.tree.serializer.JsTreeRenderer;
import io.takari.maven.plugins.dependency.tree.serializer.TreeRenderer;

import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "jstree")
public class JsTree extends AbstractTree {

  @Override
  protected TreeRenderer renderer() {
    return new JsTreeRenderer();
  }
}
