/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing;

import java.io.File;

import org.junit.Assert;

public class TestResources extends org.apache.maven.plugin.testing.resources.TestResources {

  public TestResources() {
    super("src/test/projects", "target/test-projects");
  }

  public TestResources(String projectsDir, String workDir) {
    super(projectsDir, workDir);
  }

  public static void assertFilesPresent(File basedir, String... paths) {
    if (basedir == null || paths == null || paths.length <= 0) {
      throw new IllegalArgumentException();
    }
    if (paths.length == 1) {
      Assert.assertTrue(paths[0] + " PRESENT", new File(basedir, paths[0]).isFile());
    } else {
      StringBuilder expected = new StringBuilder();
      StringBuilder actual = new StringBuilder();
      for (String path : paths) {
        expected.append(path).append("\n");
        if (!new File(basedir, path).isFile()) {
          actual.append("MISSING ");
        }
        actual.append(path).append("\n");
      }
      Assert.assertEquals(expected.toString(), actual.toString());
    }
  }

}
