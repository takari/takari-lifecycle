/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing.executor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;

// wraps maven invocation results
public class MavenExecutionResult {

  private final File basedir;
  private final List<String> log;

  MavenExecutionResult(File basedir, File logFile) throws IOException {
    this.basedir = basedir;
    List<String> log = new ArrayList<>();
    if (logFile.canRead()) {
      for (String line : Files.readAllLines(logFile.toPath(), Charset.defaultCharset())) {
        log.add(line);
      }
    }
    this.log = Collections.unmodifiableList(log);
  }

  public void assertErrorFreeLog() throws Exception {
    List<String> errors = new ArrayList<>();
    for (String line : log) {
      if (line.contains("[ERROR]")) {
        errors.add(line);
      }
    }
    Assert.assertTrue(errors.toString(), errors.isEmpty());
  }

  public void assertLogText(String text) {
    for (String line : log) {
      if (line.contains(text)) {
        return;
      }
    }
    Assert.fail("Log line not present: " + text);
  }

  public void assertNoLogText(String text) {
    for (String line : log) {
      if (line.contains(text)) {
        Assert.fail("Log line present: " + text);
      }
    }
  }

  public File getBasedir() {
    return basedir;
  }

}
