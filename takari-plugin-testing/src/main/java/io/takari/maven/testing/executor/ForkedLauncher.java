/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing.executor;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;

/**
 * @author Benjamin Bentmann
 */
class ForkedLauncher implements MavenLauncher {

  private final File mavenHome;

  private final File executable;

  private final Map<String, String> envVars;

  private final List<String> extensions;

  private final List<String> args;

  public ForkedLauncher(File mavenHome, File classworldsConf, List<String> extensions, Map<String, String> envVars, List<String> args) {
    this.args = args;
    if (mavenHome == null) {
      throw new NullPointerException();
    }
    if (classworldsConf != null) {
      throw new IllegalArgumentException("Custom classworlds configuration file is not supported");
    }

    this.mavenHome = mavenHome;
    this.envVars = envVars;
    this.extensions = extensions;
    this.executable = new File(mavenHome, "bin/mvn");
  }

  public int run(String[] cliArgs, Map<String, String> envVars, String workingDirectory, File logFile) throws IOException, LauncherException {
    CommandLine cli = new CommandLine(executable);
    cli.addArguments(args.toArray(new String[args.size()]));
    cli.addArguments(cliArgs);

    if (extensions != null && !extensions.isEmpty()) {
      cli.addArgument("-Dmaven.ext.class.path=" + toPath(extensions));
    }

    Map<String, String> env = new HashMap<>();
    if (mavenHome != null) {
      env.put("M2_HOME", mavenHome.getAbsolutePath());
    }
    if (envVars != null) {
      env.putAll(envVars);
    }
    if (envVars == null || envVars.get("JAVA_HOME") == null) {
      env.put("JAVA_HOME", System.getProperty("java.home"));
    }
    env.put("MAVEN_TERMINATE_CMD", "on");

    DefaultExecutor executor = new DefaultExecutor();
    executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
    executor.setWorkingDirectory(new File(workingDirectory));

    try (OutputStream log = new FileOutputStream(logFile)) {
      PrintStream out = new PrintStream(log);
      out.format("Maven Executor implementation: %s\n", getClass().getName());
      out.format("Maven home: %s\n", mavenHome);
      out.format("Build work directory: %s\n", workingDirectory);
      out.format("Environment: %s\n", env);
      out.format("Command line: %s\n\n", cli.toString());
      out.flush();

      PumpStreamHandler streamHandler = new PumpStreamHandler(log);
      executor.setStreamHandler(streamHandler);
      return executor.execute(cli, env); // this throws ExecuteException if process return code != 0
    } catch (ExecuteException e) {
      throw new LauncherException("Failed to run Maven: " + e.getMessage() + "\n" + cli, e);
    }
  }

  private static String toPath(List<String> strings) {
    StringBuilder sb = new StringBuilder();
    for (String string : strings) {
      if (sb.length() > 0) {
        sb.append(File.pathSeparatorChar);
      }
      sb.append(string);
    }
    return sb.toString();
  }

  @Override
  public int run(String[] cliArgs, String workingDirectory, File logFile) throws IOException, LauncherException {
    return run(cliArgs, envVars, workingDirectory, logFile);
  }

  @Override
  public String getMavenVersion() throws IOException, LauncherException {
    // TODO cleanup, there is no need to write log file, for example

    File logFile;
    try {
      logFile = File.createTempFile("maven", "log");
    } catch (IOException e) {
      throw new LauncherException("Error creating temp file", e);
    }

    // disable EMMA runtime controller port allocation, should be harmless if EMMA is not used
    Map<String, String> envVars = Collections.singletonMap("MAVEN_OPTS", "-Demma.rt.control=false");
    run(new String[] {"--version"}, envVars, null, logFile);

    List<String> logLines = Files.readAllLines(logFile.toPath(), Charset.defaultCharset());
    // noinspection ResultOfMethodCallIgnored
    logFile.delete();

    String version = extractMavenVersion(logLines);

    if (version == null) {
      throw new LauncherException("Illegal Maven output: String 'Maven' not found in the following output:\n" + join(logLines, "\n"));
    } else {
      return version;
    }
  }

  private String join(List<String> lines, String eol) {
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      sb.append(line).append(eol);
    }
    return sb.toString();
  }

  static String extractMavenVersion(List<String> logLines) {
    String version = null;

    final Pattern mavenVersion = Pattern.compile("(?i).*Maven.*? ([0-9]\\.\\S*).*");

    for (Iterator<String> it = logLines.iterator(); version == null && it.hasNext();) {
      String line = it.next();

      Matcher m = mavenVersion.matcher(line);
      if (m.matches()) {
        version = m.group(1);
      }
    }

    return version;
  }

}
