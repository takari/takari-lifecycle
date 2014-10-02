/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing.executor;

import io.takari.maven.testing.TestProperties;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenExecution {

  private final MavenLauncher launcher;

  private final TestProperties properties;

  private final File basedir;

  private final List<String> cliOptions = new ArrayList<>();

  MavenExecution(MavenLauncher launcher, TestProperties properties, File basedir) {
    this.launcher = launcher;
    this.properties = properties;
    this.basedir = basedir;
  }

  public MavenExecutionResult execute(String... goals) throws Exception {
    File logFile = new File(basedir, "log.txt");

    List<String> args = new ArrayList<>();

    File userSettings = properties.getUserSettings();
    if (userSettings != null && userSettings.isFile()) {
      args.add("-s");
      args.add(userSettings.getAbsolutePath());
    }
    args.add("-Dmaven.repo.local=" + properties.getLocalRepository().getAbsolutePath());
    args.add("-Dit-plugin.version=" + properties.getPluginVersion());
    args.addAll(cliOptions);

    for (String goal : goals) {
      args.add(goal);
    }

    launcher.run(args.toArray(new String[args.size()]), basedir.getAbsolutePath(), logFile);

    return new MavenExecutionResult(basedir, logFile);
  }

  public MavenExecution withCliOption(String string) {
    cliOptions.add(string);
    return this;
  }

  public MavenExecution withCliOptions(String... strings) {
    for (String string : strings) {
      cliOptions.add(string);
    }
    return this;
  }
}
