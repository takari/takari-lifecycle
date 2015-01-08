package io.takari.maven.plugins.configurator;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

import java.io.File;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class JarTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  @Test
  public void testCustomManifest() throws Exception {
    File basedir = resources.getBasedir("configurator/jar/project-with-manifest");
    new File(basedir, "target/classes").mkdirs(); // TODO this shouldn't be necessary
    mojos.executeMojo(basedir, "jar");
    try (JarFile jar = new JarFile(new File(basedir, "target/test-1.0.jar"))) {
      Manifest mf = jar.getManifest();
      Assert.assertEquals("custom-value", mf.getMainAttributes().getValue("Custom-Entry"));
    }
  }

  @Test
  public void testDuplicateCustomManifest() throws Exception {
    File basedir = resources.getBasedir("configurator/jar/project-with-manifest-under-target-classes");
    mojos.executeMojo(basedir, "jar");
    JarFile jar = new JarFile(new File(basedir, "target/test-1.0.jar"));
    try {
      // make sure there is only one manifest entry
      int count = 0;
      for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
        JarEntry entry = entries.nextElement();
        if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entry.getName())) {
          count++;
        }
      }
      Assert.assertEquals(1, count);
      // now check the manifest contents
      Manifest mf = jar.getManifest();
      Assert.assertEquals("custom-value", mf.getMainAttributes().getValue("Custom-Entry"));
    } finally {
      jar.close();
    }
  }
}
