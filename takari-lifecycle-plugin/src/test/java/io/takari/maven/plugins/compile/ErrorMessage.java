package io.takari.maven.plugins.compile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ErrorMessage {

  private final String compilerId;

  private final Map<String, List<String>> variants = new HashMap<String, List<String>>();

  public ErrorMessage(String compilerId) {
    this.compilerId = compilerId;
  }

  public void setSnippets(String compilerId, String... snippets) {
    variants.put(normalizeCompilerId(compilerId), Arrays.asList(snippets));
  }

  public boolean isMatch(String message) {
    int idx = 0;
    for (String snippet : variants.get(normalizeCompilerId(compilerId))) {
      idx = message.indexOf(snippet, idx);
      if (idx < 0) {
        return false;
      }
    }
    return true;
  }

  private String normalizeCompilerId(String compilerId) {
    return "forked-javac".equals(compilerId) ? "javac" : compilerId;
  }
}
