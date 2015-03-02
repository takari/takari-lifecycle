package io.takari.maven.plugins.jar;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;

public class WarTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  @Test
  public void warCreation() throws Exception {
    File basedir = resources.getBasedir("war/basic");
    mojos.executeMojo(basedir, "war");
  }
}
