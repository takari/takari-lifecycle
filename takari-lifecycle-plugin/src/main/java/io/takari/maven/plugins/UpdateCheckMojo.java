/**
 * Copyright (c) 2015 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.apache.maven.Maven;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mojo(name = "update-check", threadSafe = true)
public class UpdateCheckMojo extends AbstractMojo {

  private final Logger log = LoggerFactory.getLogger(getClass());

  public static final String REMOTE_URL = "https://update.takari.io/latest/takari-lifecycle";

  public static final String GROUP_ID = "io.takari.maven.plugins";
  public static final String ARTIFACT_ID = "takari-lifecycle-plugin";

  public static final long ONE_WEEK_MS = TimeUnit.DAYS.toMillis(7);

  public static final String UPDATE_CHECK_TIMESTAMP_PREF = "updateCheckTimestamp";

  public static final int UPDATE_TIMEOUT_MS = 30000; // 30 seconds

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    try {
      String version = getLocalVersion(getClass().getClassLoader(), GROUP_ID, ARTIFACT_ID);
      if (version == null) {
        log.debug("Could not determine {}:{} version, skipping update check", GROUP_ID, ARTIFACT_ID);
        return; // TODO generate maven pom.properties inside m2e
      }

      Preferences preferences = Preferences.userRoot().node(GROUP_ID.replace('.', '/')).node(ARTIFACT_ID).node(version.toString());
      final long timestamp = System.currentTimeMillis();
      if (timestamp - preferences.getLong(UPDATE_CHECK_TIMESTAMP_PREF, 0) < ONE_WEEK_MS) {
        // only check for update once a week
        return;
      }

      preferences.putLong(UPDATE_CHECK_TIMESTAMP_PREF, timestamp);
      preferences.flush();

      String mavenVersion = getLocalVersion(Maven.class.getClassLoader(), "org.apache.maven", "maven-core");
      String javaVersion = System.getProperty("java.version");

      // doing url encoding correctly is apparently hard in java https://github.com/google/guava/issues/1756
      String query = String.format("m=%s&j=%s", mavenVersion, javaVersion);
      URLConnection conn = new URL(REMOTE_URL + "?" + query).openConnection();
      conn.addRequestProperty("User-Agent", "takari-lifecycle/" + version.toString());
      conn.setConnectTimeout(UPDATE_TIMEOUT_MS);
      conn.setReadTimeout(UPDATE_TIMEOUT_MS);

      try (InputStream is = conn.getInputStream()) {
        String latestVersion = getVersion(is);
        if (latestVersion != null && new DefaultArtifactVersion(version).compareTo(new DefaultArtifactVersion(latestVersion)) < 0) {
          log.warn("Takari Lifecycle version {} is outdated, consider upgrade to {}", version, latestVersion);
        }
      }
    } catch (IOException | BackingStoreException ignored) {
      // this is a just courtesy to the user, no need to break the build
    }
  }

  private String getLocalVersion(ClassLoader loader, String groupId, String artifactId) throws IOException {
    String path = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
    try (InputStream is = loader.getResourceAsStream(path)) {
      return is != null ? getVersion(is) : null;
    }
  }

  private String getVersion(InputStream is) throws IOException {
    Properties p = new Properties();
    p.load(is);
    return p.getProperty("version");
  }

}
