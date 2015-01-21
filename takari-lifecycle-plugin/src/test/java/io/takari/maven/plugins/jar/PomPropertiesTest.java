package io.takari.maven.plugins.jar;

import static org.junit.Assert.assertEquals;
import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;

public class PomPropertiesTest {
  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("jar/basic");
    mojos.executeMojo(basedir, "pom-properties");

    Properties properties = new Properties();
    try (InputStream is = new FileInputStream(new File(basedir, "target/classes/META-INF/maven/io.takari.lifecycle.its/test/pom.properties"))) {
      properties.load(is);
    }

    assertEquals("io.takari.lifecycle.its", properties.getProperty("groupId"));
    assertEquals("test", properties.getProperty("artifactId"));
    assertEquals("1.0", properties.getProperty("version"));
  }
}
