package io.tesla.maven.plugins.test;

import io.takari.maven.testing.it.Verifier;
import io.takari.maven.testing.it.VerifierResult;
import io.takari.maven.testing.it.VerifierRuntime.VerifierRuntimeBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

// TODO this should be a unit test, but I could not easily make testing harness inject
// various mojo parameters
public class FilteringResourcesTest extends AbstractIntegrationTest {

  public FilteringResourcesTest(VerifierRuntimeBuilder verifierBuilder) throws Exception {
    super(verifierBuilder);
  }

  private Properties filter(String project) throws Exception {
    File basedir = resources.getBasedir(project);
    Verifier verifierBuilder = verifier.forProject(basedir);
    return filter(verifierBuilder);
  }

  private Properties filter(Verifier verifierBuilder) throws Exception, FileNotFoundException, IOException {
    VerifierResult result = verifierBuilder.execute("process-resources");

    result.assertErrorFreeLog();

    Properties properties = new Properties();
    InputStream is = new FileInputStream(new File(result.getBasedir(), "target/classes/filtered.properties"));
    try {
      properties.load(is);
    } finally {
      is.close();
    }

    return properties;
  }

  @Test
  public void testLocalRepository() throws Exception {
    String localRepository = filter("filtering-reflective-settings").getProperty("localRepository");
    Assert.assertTrue(localRepository != null && !localRepository.isEmpty());
    Assert.assertTrue(new File(localRepository).isDirectory());
  }

  @Test
  public void testUserSettingsFile() throws Exception {
    String userSettingsFile = filter("filtering-reflective-settings").getProperty("userSettingsFile");
    Assert.assertTrue(userSettingsFile != null && !userSettingsFile.isEmpty());
    // note that settings.xml may not exist
  }

  @Test
  public void testCommandLineParameters() throws Exception {
    File basedir = resources.getBasedir("filtering-command-line-parameters");
    Verifier verifierBuilder = verifier.forProject(basedir).withCliOption("-DcommandLineParameter=value");
    String commandLineParameter = filter(verifierBuilder).getProperty("commandLineParameter");
    Assert.assertEquals("value", commandLineParameter);
  }
}
