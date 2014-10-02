/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing.executor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.codehaus.plexus.classworlds.ClassWorldException;
import org.codehaus.plexus.classworlds.launcher.ConfigurationException;
import org.codehaus.plexus.classworlds.launcher.ConfigurationHandler;
import org.codehaus.plexus.classworlds.launcher.ConfigurationParser;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;

public class MavenInstallationUtils {

  public static final String MAVEN_CORE_POMPROPERTIES = "META-INF/maven/org.apache.maven/maven-core/pom.properties";

  public static final String SYSPROP_MAVEN_HOME = "maven.home";

  public static final String SYSPROP_CLASSWORLDSCONF = "classworlds.conf";

  public static String getMavenVersion(Class<?> clazz) throws IOException {
    try (InputStream is = clazz.getResourceAsStream("/" + MAVEN_CORE_POMPROPERTIES)) {
      return getMavenVersion(is);
    }
  }

  public static String getMavenVersion(InputStream is) throws IOException {
    Properties props = new Properties();
    if (is != null) {
      props.load(is);
    }
    return props.getProperty("version");
  }

  public static String getMavenVersion(File mavenHome, File classworldsConf) {
    classworldsConf = getClassworldsConf(mavenHome, classworldsConf);

    class MavenVersionFoundException extends RuntimeException {
      public final String version;

      MavenVersionFoundException(String version) {
        this.version = version;
      }
    }

    class VersionConfigHandler implements ConfigurationHandler {

      @Override
      public void setAppMain(String mainClassName, String mainRealmName) {}

      @Override
      public void addRealm(String realmName) throws DuplicateRealmException {}

      @Override
      public void addImportFrom(String relamName, String importSpec) throws NoSuchRealmException {}

      @Override
      public void addLoadFile(File file) {
        String version = null;
        try {
          if (file.isFile()) {
            try (ZipFile zip = new ZipFile(file)) {
              ZipEntry entry = zip.getEntry(MAVEN_CORE_POMPROPERTIES);
              if (entry != null) {
                try (InputStream is = zip.getInputStream(entry)) {
                  version = getMavenVersion(is);
                }
              }
            }
          } else {
            try (InputStream is = new BufferedInputStream(new FileInputStream(new File(file, MAVEN_CORE_POMPROPERTIES)))) {
              version = getMavenVersion(is);
            }
          }
          if (version != null) {
            throw new MavenVersionFoundException(version);
          }
        } catch (IOException e) {
          // assume the file does not have maven version
        }
      }

      @Override
      public void addLoadURL(URL url) {}
    };

    try {
      VersionConfigHandler configHandler = new VersionConfigHandler();
      Properties properties = new Properties(System.getProperties());
      properties.setProperty(SYSPROP_MAVEN_HOME, mavenHome.getCanonicalPath());
      ConfigurationParser configParser = new ConfigurationParser(configHandler, properties);
      try (InputStream is = new BufferedInputStream(new FileInputStream(classworldsConf))) {
        configParser.parse(is);
      }
    } catch (IOException | ClassWorldException | ConfigurationException e) {
      throw new IllegalArgumentException("Could not determine Maven version", e);
    } catch (MavenVersionFoundException e) {
      return e.version;
    }

    throw new IllegalArgumentException("Could not determine Maven version");
  }

  public static File getForcedClassworldsConf() {
    File configFile = null;
    String classworldConf = System.getProperty(SYSPROP_CLASSWORLDSCONF);
    String mavenHome = System.getProperty(SYSPROP_MAVEN_HOME);
    if (classworldConf != null) {
      configFile = new File(classworldConf);
    }
    if (configFile == null) {
      if (mavenHome != null) {
        configFile = new File(mavenHome, "bin/m2.conf");
      }
    }
    return configFile;
  }

  public static File getForcedMavenHome() {
    String mavenHome = System.getProperty(SYSPROP_MAVEN_HOME);
    if (mavenHome != null) {
      return new File(mavenHome);
    }
    return null;
  }

  public static File getClassworldsConf(File mavenHome, File classworldsConf) {
    if (classworldsConf == null) {
      classworldsConf = new File(mavenHome, "bin/m2.conf");
    }
    return classworldsConf;
  }
}
