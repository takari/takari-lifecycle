package io.tesla.maven.plugins.test;

import io.takari.maven.testing.it.VerifierResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

// TODO this should be a unit test, but I could not easily make testing harness inject
// settings with configured localRepository
public class FilteringReflectiveSettingsTest extends AbstractIntegrationTest {

  public FilteringReflectiveSettingsTest(String mavenVersion) throws Exception {
    super(mavenVersion);
  }

  private Properties properties;

  @Before
  public void setUp() throws Exception {
    File basedir = resources.getBasedir("filtering-reflective-settings");

    VerifierResult result = verifier.forProject(basedir).execute("process-resources");

    result.assertErrorFreeLog();

    Properties properties = new Properties();
    InputStream is = new FileInputStream(new File(basedir, "target/classes/settings.properties"));
    try {
      properties.load(is);
    } finally {
      is.close();
    }
    this.properties = properties;
  }

  @Test
  public void testLocalRepository() throws Exception {
    String localRepository = properties.getProperty("localRepository");
    Assert.assertTrue(localRepository != null && !localRepository.isEmpty());
    Assert.assertTrue(new File(localRepository).isDirectory());
  }

  @Test
  public void testUserSettingsFile() throws Exception {
    String userSettingsFile = properties.getProperty("userSettingsFile");
    Assert.assertTrue(userSettingsFile != null && !userSettingsFile.isEmpty());
    // note that settings.xml may not exist
  }
}
