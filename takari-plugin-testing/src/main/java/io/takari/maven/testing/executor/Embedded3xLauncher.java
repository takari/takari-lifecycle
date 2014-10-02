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

import static io.takari.maven.testing.executor.MavenInstallationUtils.SYSPROP_MAVEN_HOME;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.codehaus.plexus.classworlds.ClassWorldException;
import org.codehaus.plexus.classworlds.launcher.ConfigurationException;
import org.codehaus.plexus.classworlds.launcher.ConfigurationHandler;
import org.codehaus.plexus.classworlds.launcher.ConfigurationParser;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;


/**
 * Launches an embedded Maven 3.x instance from some Maven installation directory.
 * 
 * @author Benjamin Bentmann
 */
class Embedded3xLauncher implements MavenLauncher {

  private static class ClassworldsConfiguration implements ConfigurationHandler {

    private String mainType;
    private String mainRealm;
    private LinkedHashMap<String, List<String>> realms = new LinkedHashMap<>();
    private List<String> curEntries;

    @Override
    public void setAppMain(String mainType, String mainRealm) {
      this.mainType = mainType;
      this.mainRealm = mainRealm;
    }

    @Override
    public void addRealm(String realm) throws DuplicateRealmException {
      if (!realms.containsKey(realm)) {
        curEntries = new ArrayList<>();
        realms.put(realm, curEntries);
      }
    }

    @Override
    public void addImportFrom(String relamName, String importSpec) throws NoSuchRealmException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void addLoadFile(File file) {
      if (curEntries == null) {
        throw new IllegalStateException();
      }
      curEntries.add(file.getAbsolutePath());
    }

    public void addEntries(String realm, List<String> locations) {
      List<String> entries = realms.get(realm);
      if (entries == null) {
        throw new IllegalStateException();
      }
      entries.addAll(0, locations);
    }

    @Override
    public void addLoadURL(URL url) {
      if (curEntries == null) {
        throw new IllegalStateException();
      }
      curEntries.add(url.toExternalForm());
    }

    public void store(OutputStream os) throws IOException {
      BufferedWriter out = new BufferedWriter(new OutputStreamWriter(os, "UTF-8")); //$NON-NLS-1$
      out.write(String.format("main is %s from %s\n", mainType, mainRealm));
      for (Map.Entry<String, List<String>> realm : realms.entrySet()) {
        out.write(String.format("[%s]\n", realm.getKey()));
        for (String entry : realm.getValue()) {
          out.write(String.format("load %s\n", entry));
        }
      }
      out.flush();
    }

  }

  private static class Key {

    private final File mavenHome;
    private final File classworldConf;
    private final List<URL> bootclasspath;
    private final List<String> extensions;
    private final List<String> args;

    public Key(File mavenHome, File classworldConf, List<URL> bootclasspath, List<String> extensions, List<String> args) {
      this.mavenHome = mavenHome;
      this.classworldConf = classworldConf;
      this.bootclasspath = clone(bootclasspath);
      this.extensions = clone(extensions);
      this.args = clone(args);
    }

    @Override
    public int hashCode() {
      int hash = 17;
      hash = hash * 31 + mavenHome.hashCode();
      hash = hash * 31 + (classworldConf != null ? classworldConf.hashCode() : 0);
      hash = hash * 31 + (bootclasspath != null ? bootclasspath.hashCode() : 0);
      hash = hash * 31 + (extensions != null ? extensions.hashCode() : 0);
      hash = hash * 31 + (args != null ? args.hashCode() : 0);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Key)) {
        return false;
      }
      Key other = (Key) obj;
      return eq(mavenHome, other.mavenHome) && eq(classworldConf, other.classworldConf) && eq(bootclasspath, other.bootclasspath) && eq(extensions, other.extensions) && eq(args, other.args);
    }

    private static <T> List<T> clone(List<T> origin) {
      return origin != null ? new ArrayList<>(origin) : null;
    }

    private static <T> boolean eq(T a, T b) {
      return a != null ? a.equals(b) : b == null;
    }
  }

  private static final Map<Key, Embedded3xLauncher> CACHE = new HashMap<>();

  private final File mavenHome;

  private final Object classWorld;

  private final Object mavenCli;

  private final Method doMain;

  private final List<String> args;

  private Embedded3xLauncher(File mavenHome, Object classWorld, Object mavenCli, Method doMain, List<String> args) {
    this.mavenHome = mavenHome;
    this.classWorld = classWorld;
    this.mavenCli = mavenCli;
    this.doMain = doMain;
    this.args = args;
  }

  /**
   * Launches an embedded Maven 3.x instance from some Maven installation directory.
   */
  public static Embedded3xLauncher createFromMavenHome(File mavenHome, File classworldConf, List<String> extensions, List<String> args) throws LauncherException {
    if (!isValidMavenHome(mavenHome)) {
      throw new LauncherException("Invalid Maven home directory " + mavenHome);
    }

    List<URL> bootclasspath = toClasspath(System.getProperty("maven.bootclasspath"));

    Properties originalProperties = copy(System.getProperties());
    System.setProperty(SYSPROP_MAVEN_HOME, mavenHome.getAbsolutePath());

    try {
      final Key key = new Key(mavenHome, classworldConf, bootclasspath, extensions, args);
      Embedded3xLauncher launcher = CACHE.get(key);
      if (launcher == null) {
        launcher = createFromMavenHome0(mavenHome, classworldConf, bootclasspath, extensions, args);
        CACHE.put(key, launcher);
      }
      return launcher;
    } finally {
      System.setProperties(originalProperties);
    }
  }

  private static boolean isValidMavenHome(File mavenHome) {
    if (mavenHome == null) {
      return false;
    }

    if ("WORKSPACE".equals(mavenHome.getPath()) || "EMBEDDED".equals(mavenHome.getPath())) {
      return true;
    }

    return mavenHome.isDirectory();
  }

  private static List<URL> toClasspath(String string) throws LauncherException {
    if (string == null) {
      return null;
    }
    StringTokenizer st = new StringTokenizer(string, File.pathSeparator);
    List<URL> classpath = new ArrayList<>();
    while (st.hasMoreTokens()) {
      try {
        classpath.add(new File(st.nextToken()).toURI().toURL());
      } catch (MalformedURLException e) {
        throw new LauncherException("Invalid launcher classpath " + string, e);
      }
    }
    return classpath;
  }

  private static Embedded3xLauncher createFromMavenHome0(File mavenHome, File classworldConf, List<URL> bootclasspath, List<String> extensions, List<String> args) throws LauncherException {
    File configFile = MavenInstallationUtils.getClassworldsConf(mavenHome, classworldConf);

    ClassLoader bootLoader = getBootLoader(mavenHome, bootclasspath);

    ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(bootLoader);
    try {
      ClassworldsConfiguration config = new ClassworldsConfiguration();
      ConfigurationParser configParser = new ConfigurationParser(config, System.getProperties());
      try (InputStream is = new BufferedInputStream(new FileInputStream(configFile))) {
        configParser.parse(is);
      }
      if (extensions != null && !extensions.isEmpty()) {
        config.addEntries("plexus.core", extensions);
      }
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      config.store(buf);

      // Launcher launcher = new org.codehaus.plexus.classworlds.launcher.Launcher()
      // launcher.configure(buf)
      // ClassWorld classWorld = launcher.getWorld()
      // MavenCli mavenCli = launcher.getMainClass().newInstance(classWorld)

      Class<?> launcherClass = bootLoader.loadClass("org.codehaus.plexus.classworlds.launcher.Launcher");
      Object launcher = launcherClass.newInstance();
      launcherClass.getMethod("configure", new Class[] {InputStream.class}).invoke(launcher, new ByteArrayInputStream(buf.toByteArray()));
      Object classWorld = launcherClass.getMethod("getWorld").invoke(launcher);

      Class<?> cliClass = (Class<?>) launcherClass.getMethod("getMainClass").invoke(launcher);
      Object mavenCli = cliClass.getConstructor(classWorld.getClass()).newInstance(classWorld);

      Method doMain = cliClass.getMethod("doMain", //
          String[].class, String.class, PrintStream.class, PrintStream.class);

      return new Embedded3xLauncher(mavenHome, classWorld, mavenCli, doMain, args);
    } catch (ReflectiveOperationException | IOException | ClassWorldException | ConfigurationException e) {
      throw new LauncherException("Invalid Maven home directory " + mavenHome, e);
    } finally {
      Thread.currentThread().setContextClassLoader(oldClassLoader);
    }
  }

  private static ClassLoader getBootLoader(File mavenHome, List<URL> classpath) {
    List<URL> urls = classpath;

    if (urls == null) {
      urls = new ArrayList<URL>();

      File bootDir = new File(mavenHome, "boot");
      addUrls(urls, bootDir);
    }

    if (urls.isEmpty()) {
      throw new IllegalArgumentException("Invalid Maven home directory " + mavenHome);
    }

    URL[] ucp = urls.toArray(new URL[urls.size()]);

    return new URLClassLoader(ucp, ClassLoader.getSystemClassLoader().getParent());
  }

  private static void addUrls(List<URL> urls, File directory) {
    File[] jars = directory.listFiles();

    if (jars != null) {
      for (int i = 0; i < jars.length; i++) {
        File jar = jars[i];

        if (jar.getName().endsWith(".jar")) {
          try {
            urls.add(jar.toURI().toURL());
          } catch (MalformedURLException e) {
            throw (RuntimeException) new IllegalStateException().initCause(e);
          }
        }
      }
    }
  }

  @Override
  public int run(String[] cliArgs, String workingDirectory, File logFile) throws IOException, LauncherException {
    PrintStream out = (logFile != null) ? new PrintStream(new FileOutputStream(logFile)) : System.out;
    try {
      Properties originalProperties = copy(System.getProperties());
      System.setProperties(null);
      System.setProperty(SYSPROP_MAVEN_HOME, mavenHome.getAbsolutePath());
      System.setProperty("user.dir", new File(workingDirectory).getAbsolutePath());

      ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(mavenCli.getClass().getClassLoader());
      try {
        Set<String> origRealms = getRealmIds();

        List<String> args = new ArrayList<>(this.args);
        args.addAll(Arrays.asList(cliArgs));

        out.format("Maven Executor implementation: %s\n", getClass().getName());
        out.format("Maven home: %s\n", mavenHome);
        out.format("Build work directory: %s\n", workingDirectory);
        out.format("Execution parameters: %s\n\n", args);

        Object result = doMain.invoke(mavenCli, //
            args.toArray(new String[args.size()]), workingDirectory, out, out);

        Set<String> realms = getRealmIds();
        realms.removeAll(origRealms);
        for (String realmId : realms) {
          disposeRealm(realmId);
        }

        return ((Number) result).intValue();
      } finally {
        Thread.currentThread().setContextClassLoader(originalClassLoader);

        System.setProperties(originalProperties);
      }
    } catch (IllegalAccessException e) {
      throw new LauncherException("Failed to run Maven: " + e.getMessage(), e);
    } catch (InvocationTargetException e) {
      throw new LauncherException("Failed to run Maven: " + e.getMessage(), e);
    } finally {
      if (logFile != null) {
        out.close();
      }
    }
  }

  private static Properties copy(Properties properties) {
    Properties copy = new Properties();
    for (String key : properties.stringPropertyNames()) {
      copy.put(key, properties.getProperty(key));
    }
    return copy;
  }

  @Override
  public String getMavenVersion() throws LauncherException {
    try {
      String version = MavenInstallationUtils.getMavenVersion(mavenCli.getClass());
      if (version != null) {
        return version;
      }
    } catch (IOException e) {
      throw new LauncherException("Failed to read Maven version", e);
    }

    throw new LauncherException("Could not determine embedded Maven version");
  }

  private Set<String> getRealmIds() {
    Set<String> result = new HashSet<>();

    try {
      Collection<?> realms = (Collection<?>) classWorld.getClass().getMethod("getRealms").invoke(classWorld);
      for (Object realm : realms) {
        String id = (String) realm.getClass().getMethod("getId").invoke(realm);
        result.add(id);
      }
    } catch (RuntimeException | ReflectiveOperationException e) {
      // best-effort, silently ignore failures
    }

    return result;
  }

  private void disposeRealm(String id) {
    try {
      classWorld.getClass().getMethod("disposeRealm", String.class).invoke(classWorld, id);
    } catch (RuntimeException | ReflectiveOperationException e) {
      // best-effort, silently ignore failures
    }
  }

}
