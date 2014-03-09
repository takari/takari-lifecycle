package io.takari.maven.plugins.compile;

import java.util.HashMap;
import java.util.Map;

public class ErrorMessage {

  private final String compilerId;

  private final Map<String, String> variants = new HashMap<String, String>();

  public ErrorMessage(String compilerId) {
    this.compilerId = compilerId;
  }

  public void setText(String compilerId, String text) {
    variants.put(normalizeCompilerId(compilerId), text);
  }

  public String getText() {
    return variants.get(normalizeCompilerId(compilerId));
  }

  private String normalizeCompilerId(String compilerId) {
    return "forked-javac".equals(compilerId) ? "javac" : compilerId;
  }
}
