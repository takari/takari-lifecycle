/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class TestDependencies {

  public static final String KEY_CLASSPATH = "classpath";

  private final TestProperties properties;

  public TestDependencies(TestProperties properties) {
    if (properties == null) {
      throw new NullPointerException();
    }
    this.properties = properties;
  }

  /**
   * Returns location of the current project classes, i.e. target/classes directory, and all project dependencies with scope=runtime.
   */
  public List<File> getRuntimeClasspath() {
    StringTokenizer st = new StringTokenizer(properties.get(KEY_CLASSPATH), File.pathSeparator);
    List<File> dependencies = new ArrayList<>();
    while (st.hasMoreTokens()) {
      dependencies.add(new File(st.nextToken()));
    }
    return dependencies;
  }

}
