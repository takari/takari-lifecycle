package io.takari.maven.plugins.jar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import io.takari.hash.FingerprintSha1Streaming;
import io.takari.incrementalbuild.maven.testing.BuildAvoidanceRule;

import java.io.File;
import java.nio.charset.Charset;

import org.apache.maven.plugin.testing.resources.TestResources;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.io.Files;

public class JarTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final BuildAvoidanceRule mojos = new BuildAvoidanceRule();

  @Test
  public void resources() throws Exception {
    // Generate some resources to JAR
    File basedir = resources.getBasedir("jar/project-with-resources");
    mojos.executeMojo(basedir, "process-resources");
    File resource = new File(basedir, "target/classes/resource.txt");
    assertTrue(resource.exists());
    String line = Files.readFirstLine(resource, Charset.defaultCharset());
    assertTrue(line.contains("resource.txt"));
    //
    // Generate the JAR a first time and use our jar fingerprinting
    //
    mojos.executeMojo(basedir, "jar");
    File jar0 = new File(basedir, "target/test-1.0.jar");
    assertTrue(jar0.exists());
    String fingerprint0 = new FingerprintSha1Streaming().fingerprint(jar0);
    //
    // Generate the JAR a second time and ensure that the fingerprint is still the same when
    // the JAR contains the same content. The outer SHA1 of a JAR built at two points in time will
    // be different even though the content has not changed.
    //
    mojos.executeMojo(basedir, "jar");
    File jar1 = new File(basedir, "target/test-1.0.jar");
    Assert.assertTrue(jar1.exists());
    String fingerprint1 = new FingerprintSha1Streaming().fingerprint(jar1);
    assertEquals("We expect the JAR to have the same fingerprint after repeated builds.",
        fingerprint0, fingerprint1);
  }
}
