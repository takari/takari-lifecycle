package io.takari.maven.testing.it;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

public class JUnitParameters {

  public static Iterable<Object[]> fromVersions(String... versions) throws IOException {
    List<Object[]> parameters = new ArrayList<>();

    File forcedHome = MavenUtils.getForcedMavenHome();
    File forcedClassworldConf = MavenUtils.getForcedClassworldsConf();

    if (forcedHome != null) {
      parameters.add(new Object[] {forcedHome, forcedClassworldConf, MavenUtils.getMavenVersion(forcedHome, forcedClassworldConf)});
    } else {
      for (String version : versions) {
        File mavenHome = new File("target/maven-installation/apache-maven-" + version).getCanonicalFile();
        Assert.assertTrue("Can't locate maven home, make sure to run 'mvn generate-test-resources': " + mavenHome, mavenHome.isDirectory());
        parameters.add(new Object[] {mavenHome, null, MavenUtils.getMavenVersion(mavenHome, null)});
      }
    }
    return parameters;
  }

}
