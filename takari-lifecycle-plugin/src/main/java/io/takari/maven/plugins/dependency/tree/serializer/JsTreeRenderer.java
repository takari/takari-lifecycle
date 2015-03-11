package io.takari.maven.plugins.dependency.tree.serializer;

import io.takari.maven.plugins.dependency.tree.WebUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.UUID;

import org.eclipse.aether.graph.DependencyNode;

import com.google.common.base.Splitter;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

// [
// {"title": "Item 1"},
// {"title": "Folder 2", "isFolder": true, "key": "folder2",
// "children": [
// {"title": "Sub-item 2.1"},
// {"title": "Sub-item 2.2"}
// ]
// },
// {"title": "Folder 3", "isFolder": true, "key": "folder3",
// "children": [
// {"title": "Sub-item 3.1"},
// {"title": "Sub-item 3.2"}
// ]
// },
// {"title": "Item 5"}
// ]

public class JsTreeRenderer implements TreeRenderer {

  private StringWriter out = new StringWriter();

  public void render(DependencyNode root) {

    out.write("[");
    renderNode(root);
    out.write("]");

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    JsonParser jp = new JsonParser();
    JsonElement je = jp.parse(out.toString());
    String prettyJsonString = gson.toJson(je);

    try {
      InputStream assetsIn = Thread.currentThread().getContextClassLoader().getResourceAsStream("dependency/tree/resources.txt");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ByteStreams.copy(assetsIn, baos);

      Iterable<String> assets = Splitter.onPattern("\r?\n") //
          .trimResults() //
          .omitEmptyStrings() //
          .split(baos.toString());

      File tmpdir = new File(System.getProperty("java.io.tmpdir"));
      File outputDirectory = new File(tmpdir.getAbsolutePath() + "/" + UUID.randomUUID().toString());
      outputDirectory.mkdirs();

      for (String asset : assets) {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("dependency/tree/" + asset);
        if (in != null) {
          File f = new File(outputDirectory, asset);
          f.getParentFile().mkdirs();
          ByteStreams.copy(in, new FileOutputStream(f));
        }
      }

      File dhv = new File(outputDirectory, "dhv.html");
      String dhvContent = Files.toString(dhv, Charset.defaultCharset());
      dhvContent = dhvContent.replace("{ url: \"graph.json\" }", prettyJsonString);
      Files.write(dhvContent, dhv, Charset.defaultCharset());
      WebUtils.openUrl(dhv.toURI().toURL().toExternalForm());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  void renderNode(DependencyNode node) {
    out.write("{ \"title\" : \"" + renderDependencyNode(node) + "\", \"expanded\": true");
    if (node.getChildren().size() > 0) {
      out.write(", \"children\" : [");
      int children = node.getChildren().size();
      for (int i = 0; i < children; i++) {
        DependencyNode n = node.getChildren().get(i);
        renderNode(n);
        if (i != (children - 1)) {
          out.write(",");
        }
      }
      out.write("]");
    }
    out.write(" }");
  }

  private String renderDependencyNode(DependencyNode node) {
    if (node.getDependency() == null) {
      return node.toString();
    } else {
      return node.getDependency().getArtifact().getArtifactId() + " : " + node.getDependency().getArtifact().getVersion() + " [" + node.getDependency().getScope() + "]";
    }
  }
}
