package io.tesla.maven.plugins.dependency;

import io.tesla.maven.plugins.dependency.tree.serializer.JsTreeRenderer;
import io.tesla.maven.plugins.dependency.tree.serializer.TreeRenderer;

import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "jstree")
public class JsTree extends AbstractTree {

  @Override
  protected TreeRenderer renderer() {
    return new JsTreeRenderer();
  }
}
