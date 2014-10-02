/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing.executor;

import static org.eclipse.m2e.workspace.WorkspaceState.SYSPROP_STATEFILE_LOCATION;
import io.takari.maven.testing.TestProperties;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavenRuntime {

  private final MavenLauncher launcher;

  private final TestProperties properties;

  public static class MavenRuntimeBuilder {

    protected final TestProperties properties;

    protected final File mavenHome;

    protected final File classworldsConf;

    protected final List<String> extensions = new ArrayList<>();

    protected final List<String> args = new ArrayList<>();

    MavenRuntimeBuilder(File mavenHome, File classworldsConf) {
      this.properties = new TestProperties();
      this.mavenHome = mavenHome;
      this.classworldsConf = classworldsConf;

      String workspaceState = System.getProperty(SYSPROP_STATEFILE_LOCATION);
      if (workspaceState == null) {
        workspaceState = properties.get("workspaceStateProperties");
      }
      String workspaceResolver = properties.get("workspaceResolver");
      if (isFile(workspaceState) && isFile(workspaceResolver)) {
        if ("3.2.1".equals(MavenInstallationUtils.getMavenVersion(mavenHome, classworldsConf))) {
          throw new IllegalArgumentException("Maven 3.2.1 is not supported, see https://jira.codehaus.org/browse/MNG-5591");
        }
        args.add("-D" + SYSPROP_STATEFILE_LOCATION + "=" + workspaceState);
        extensions.add(workspaceResolver);
      }
      // TODO decide if workspace resolution must be enabled and enforced
    }

    private static boolean isFile(String path) {
      return path != null && new File(path).isFile();
    }

    public MavenRuntimeBuilder withExtension(File extensionLocation) {
      extensions.add(extensionLocation.getAbsolutePath());
      return this;
    }

    public MavenRuntimeBuilder withExtensions(Collection<File> extensionLocations) {
      for (File extensionLocation : extensionLocations) {
        extensions.add(extensionLocation.getAbsolutePath());
      }
      return this;
    }

    public MavenRuntimeBuilder withCliOptions(String... options) {
      for (String option : options) {
        args.add(option);
      }
      return this;
    }

    public ForkedMavenRuntimeBuilder forkedBuilder() {
      return new ForkedMavenRuntimeBuilder(mavenHome, classworldsConf, extensions, args);
    }

    public MavenRuntime build() throws Exception {
      Embedded3xLauncher launcher = Embedded3xLauncher.createFromMavenHome(mavenHome, classworldsConf, extensions, args);
      return new MavenRuntime(launcher, properties);
    }
  }

  public static class ForkedMavenRuntimeBuilder extends MavenRuntimeBuilder {

    private Map<String, String> environment;

    ForkedMavenRuntimeBuilder(File mavenHome, File classworldsConf) {
      super(mavenHome, classworldsConf);
    }

    ForkedMavenRuntimeBuilder(File mavenHome, File classworldsConf, List<String> extensions, List<String> args) {
      super(mavenHome, classworldsConf);
      this.extensions.addAll(extensions);
      this.args.addAll(args);
    }

    public ForkedMavenRuntimeBuilder withEnvironment(Map<String, String> environment) {
      this.environment = new HashMap<>(environment);
      return this;
    }

    @Override
    public MavenRuntime build() {
      ForkedLauncher launcher = new ForkedLauncher(mavenHome, classworldsConf, extensions, environment, args);
      return new MavenRuntime(launcher, properties);
    }
  }

  MavenRuntime(MavenLauncher launcher, TestProperties properties) {
    this.launcher = launcher;
    this.properties = properties;
  }

  public static MavenRuntimeBuilder builder(File mavenHome, File classworldsConf) {
    return new MavenRuntimeBuilder(mavenHome, classworldsConf);
  }

  public static ForkedMavenRuntimeBuilder forkedBuilder(File mavenHome) {
    return new ForkedMavenRuntimeBuilder(mavenHome, null);
  }

  public MavenExecution forProject(File basedir) {
    return new MavenExecution(launcher, properties, basedir);
  }
}
