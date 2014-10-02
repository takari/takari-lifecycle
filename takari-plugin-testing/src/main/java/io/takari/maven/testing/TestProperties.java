/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Assert;


public class TestProperties {

  public static final String PROP_USER_SETTING = "userSettings";

  public static final String PROP_LOCAL_REPOSITORY = "localRepository";

  private final Map<String, String> properties;

  public TestProperties() {
    try {
      this.properties = loadProperties();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected Map<String, String> loadProperties() throws IOException {
    Properties p = new Properties();
    try (InputStream os = getClass().getClassLoader().getResourceAsStream("test.properties")) {
      p.load(os);
    }
    Map<String, String> properties = new HashMap<>();
    for (String key : p.stringPropertyNames()) {
      properties.put(key, p.getProperty(key));
    }
    return Collections.unmodifiableMap(properties);
  }

  public String get(String key) {
    return properties.get(key);
  }

  public File getUserSettings() {
    // can be null
    String path = properties.get(PROP_USER_SETTING);
    if (path == null) {
      return null;
    }
    File file = new File(path);
    Assert.assertTrue("Can read user settings.xml", file.canRead());
    return file;
  }

  public File getLocalRepository() {
    // can't be null
    String path = properties.get(PROP_LOCAL_REPOSITORY);
    Assert.assertNotNull("Local repository specified", path);
    File dir = new File(path);
    Assert.assertTrue("Local repository is a directory : " + dir, dir.isDirectory());
    return dir;
  }

  public String getPluginVersion() {
    return properties.get("project.version");
  }
}
