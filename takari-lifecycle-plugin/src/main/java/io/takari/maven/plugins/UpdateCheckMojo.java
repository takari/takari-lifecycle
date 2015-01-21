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
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mojo(name = "update-check")
public class UpdateCheckMojo extends AbstractMojo {

  private final Logger log = LoggerFactory.getLogger(getClass());

  public static final String REMOTE_URL = "https://download.takari.io/latest/takari-lifecycle";

  public static final String GROUP_ID = "io.takari.maven.plugins";
  public static final String ARTIFACT_ID = "takari-lifecycle";

  public static final long ONE_WEEK_MS = TimeUnit.DAYS.toMillis(7);

  public static final String UPDATE_CHECK_TIMESTAMP_PREF = "updateCheckTimestamp";

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    try {
      ArtifactVersion local = getLocalVersion(getClass().getClassLoader(), GROUP_ID, ARTIFACT_ID);
      if (local == null) {
        return; // TODO generate maven pom.properties inside m2e
      }

      ArtifactVersion maven = getLocalVersion(Maven.class.getClassLoader(), "org.apache.maven", "maven-core");

      Preferences preferences = Preferences.userRoot().node(GROUP_ID.replace('.', '/')).node(ARTIFACT_ID).node(local.toString());
      final long timestamp = System.currentTimeMillis();
      if (timestamp - preferences.getLong(UPDATE_CHECK_TIMESTAMP_PREF, 0) < ONE_WEEK_MS) {
        // only check for update once a week
        return;
      }

      preferences.putLong(UPDATE_CHECK_TIMESTAMP_PREF, timestamp);
      preferences.flush();

      URLConnection conn = new URL(REMOTE_URL).openConnection();
      conn.addRequestProperty("User-Agent", "takari-lifecycle/" + local.toString() + "/" + maven);

      try (InputStream is = conn.getInputStream()) {
        ArtifactVersion remote = getVersion(is);
        if (local.compareTo(remote) < 0) {
          log.warn("Takari Lifecycle version {} is outdated, consider upgrade to {}", local, remote);
        }
      }
    } catch (IOException | BackingStoreException ignored) {
      // this is a just courtesy to the user, no need to break the build
    }
  }

  private ArtifactVersion getLocalVersion(ClassLoader loader, String groupId, String artifactId) throws IOException {
    String path = "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
    try (InputStream is = loader.getResourceAsStream(path)) {
      return is != null ? getVersion(is) : null;
    }
  }

  private ArtifactVersion getVersion(InputStream is) throws IOException {
    Properties p = new Properties();
    p.load(is);
    return new DefaultArtifactVersion(p.getProperty("version"));
  }

}
