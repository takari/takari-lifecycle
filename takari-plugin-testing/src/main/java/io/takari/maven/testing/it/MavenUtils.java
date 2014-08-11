package io.takari.maven.testing.it;

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
import org.junit.Assert;

class MavenUtils {

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

  public static String getForcedVersion() {
    File configFile = getForcedClassworldsConf();

    if (configFile == null) {
      return null;
    }

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

    VersionConfigHandler configHandler = new VersionConfigHandler();
    ConfigurationParser configParser = new ConfigurationParser(configHandler, System.getProperties());
    try (InputStream is = new BufferedInputStream(new FileInputStream(configFile))) {
      configParser.parse(is);
    } catch (IOException | ClassWorldException | ConfigurationException e) {
      // assume the version is not forced
    } catch (MavenVersionFoundException e) {
      return e.version;
    }

    return null;
  }

  private static File getForcedClassworldsConf() {
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

  public static boolean isForcedVersion() {
    return getForcedClassworldsConf() != null;
  }

  public static File getMavenHome(String mavenVersion) {
    if (isForcedVersion()) {
      return new File(System.getProperty(SYSPROP_MAVEN_HOME)); // enforce not null
    }
    File mavenHome = new File("target/maven-installation/apache-maven-" + mavenVersion);
    Assert.assertTrue("Can't locate maven home, make sure to run 'mvn generate-test-resources': " + mavenHome, mavenHome.isDirectory());
    return mavenHome;
  }
}
