package io.takari.maven.plugins.pgp;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

public class PgpTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  @Test
  public void artifactSigning() throws Exception {
    File basedir = resources.getBasedir("pgp/basic");
    mojos.executeMojo(basedir, "jar");
    mojos.executeMojo(basedir, "sign");
    assertTrue(new File(basedir, "target/test-1.0.pom").exists());
    assertTrue(new File(basedir, "target/test-1.0.pom.asc").exists());
    assertTrue(new File(basedir, "target//test-1.0.jar").exists());
    assertTrue(new File(basedir, "target/test-1.0.jar.asc").exists());
  }
}
