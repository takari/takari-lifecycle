/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing.executor.junit;

import io.takari.maven.testing.executor.MavenInstallationUtils;
import io.takari.maven.testing.executor.MavenInstallations;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

/**
 * Runs JUnit4 tests with one or more Maven runtimes. The test class must have public constructor with single parameter of type {@linkplain MavenRuntimeBuilder VerifierRuntimeBuilder}.
 * <p/>
 * Test Maven runtimes are located in the following order:
 * 
 * <ol>
 * <li>If {@code -Dmaven.home} (and optionally {@code -Dclassworlds.conf}) is specified, the tests will be executed with the specified Maven installation. This is what is used in Eclipse Maven JUnit
 * Test launch configuration to implement "Override test Maven runtime". Can also be useful to run tests with custom or snapshot Maven build specified from pom.xml.</li>
 * <li>If {@linkplain MavenInstallations @MavenInstallations} and/or {@linkplain MavenVersions @MavenVersions} is specified, the tests will run with all configured Maven installations and versions</li>
 * </ol>
 */
public class MavenJUnitTestRunner extends Suite {

  private static class SingleMavenInstallationRunner extends BlockJUnit4ClassRunner {

    private final File mavenHome;

    private final File classworldsConf;

    private final String name;

    SingleMavenInstallationRunner(Class<?> klass, File mavenHome, File classworldsConf, String name) throws InitializationError {
      super(klass);
      this.mavenHome = mavenHome;
      this.classworldsConf = classworldsConf;
      this.name = name;
    }

    @Override
    protected Object createTest() throws Exception {
      MavenRuntimeBuilder builder = MavenRuntime.builder(mavenHome, classworldsConf);
      return getTestClass().getJavaClass().getConstructor(MavenRuntimeBuilder.class).newInstance(builder);
    }

    @Override
    protected void validateZeroArgConstructor(List<Throwable> errors) {
      try {
        getTestClass().getJavaClass().getConstructor(MavenRuntimeBuilder.class);
      } catch (NoSuchMethodException e) {
        errors.add(e);
      }
    }

    @Override
    protected String getName() {
      return "[" + name + "]";
    }

    @Override
    protected String testName(FrameworkMethod method) {
      return method.getName() + getName();
    }

    @Override
    protected Statement classBlock(RunNotifier notifier) {
      return childrenInvoker(notifier);
    }
  }

  public MavenJUnitTestRunner(Class<?> clazz) throws Throwable {
    super(clazz, getRunners(clazz));
  }

  private static List<Runner> getRunners(Class<?> clazz) throws Throwable {
    File forcedMavenHome = MavenInstallationUtils.getForcedMavenHome();
    File forcedClassworldsConf = MavenInstallationUtils.getForcedClassworldsConf();

    if (forcedMavenHome != null) {
      if (forcedMavenHome.isDirectory() || (forcedClassworldsConf != null && forcedClassworldsConf.isFile())) {
        String version = MavenInstallationUtils.getMavenVersion(forcedMavenHome, forcedClassworldsConf);
        return Collections.<Runner>singletonList(new SingleMavenInstallationRunner(clazz, forcedMavenHome, forcedClassworldsConf, version));
      }
      throw new InitializationError(new Exception("Invalid -Dmaven.home=" + forcedMavenHome.getAbsolutePath()));
    }

    List<Throwable> errors = new ArrayList<>();
    List<Runner> runners = new ArrayList<>();

    MavenInstallations installations = clazz.getAnnotation(MavenInstallations.class);
    if (installations != null) {
      for (String installation : installations.value()) {
        File mavenHome = new File(installation).getCanonicalFile();
        if (mavenHome.isDirectory()) {
          runners.add(new SingleMavenInstallationRunner(clazz, mavenHome, null, installation));
        } else {
          errors.add(new Exception("Invalid maven installation location " + installation));
        }
      }
    }

    MavenVersions versions = clazz.getAnnotation(MavenVersions.class);
    if (versions != null) {
      for (String version : versions.value()) {
        File mavenHome = new File("target/maven-installation/apache-maven-" + version).getCanonicalFile();
        if (mavenHome.isDirectory()) {
          runners.add(new SingleMavenInstallationRunner(clazz, mavenHome, null, version));
        } else {
          errors.add(new Exception("Can't locate maven home for version " + version + ", make sure to run 'mvn generate-test-resources'"));
        }
      }
    }

    if (!errors.isEmpty()) {
      throw new InitializationError(errors);
    }

    if (runners.isEmpty()) {
      throw new InitializationError(new Exception("No configured test maven runtime"));
    }

    return runners;
  }
}
