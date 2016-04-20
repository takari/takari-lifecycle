package io.takari.maven.plugins.compile;

import java.util.HashMap;
import java.util.Map;

public class ErrorMessage {

  private final String compilerId;

  private final Map<String, String[]> variants = new HashMap<>();

  public ErrorMessage(String compilerId) {
    this.compilerId = compilerId;
  }

  public void setSnippets(String compilerId, String... snippets) {
    variants.put(normalizeCompilerId(compilerId), snippets);
  }

  public boolean isMatch(String message) {
    return isMatch(message, variants.get(normalizeCompilerId(compilerId)));
  }

  private String normalizeCompilerId(String compilerId) {
    return "forked-javac".equals(compilerId) ? "javac" : compilerId;
  }

  public static boolean isMatch(String message, String... snippets) {
    int idx = 0;
    for (String snippet : snippets) {
      idx = message.indexOf(snippet, idx);
      if (idx < 0) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (String snippet : variants.get(compilerId)) {
      if (sb.length() > 0) {
        sb.append(" ... ");
      }
      sb.append(snippet);
    }
    return sb.toString();
  }
}
