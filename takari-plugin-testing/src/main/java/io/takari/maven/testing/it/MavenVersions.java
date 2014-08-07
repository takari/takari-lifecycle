package io.takari.maven.testing.it;

import java.util.ArrayList;
import java.util.List;

public class MavenVersions {

  public static Iterable<Object[]> asJunitParameters(String... versions) {
    List<Object[]> parameters = new ArrayList<>();
    String forcedVersion = MavenUtils.getForcedVersion();
    if (forcedVersion != null) {
      parameters.add(new Object[] {forcedVersion});
    } else {
      for (String version : versions) {
        parameters.add(new Object[] {version});
      }
    }
    return parameters;
  }

}
