package io.tesla.maven.plugins.test;

import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.ForkedMavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

// TODO this should be a unit test, but I could not easily make testing harness inject
// various mojo parameters
public class FilteringResourcesTest extends AbstractIntegrationTest {

  private final ForkedMavenRuntimeBuilder verifierForkedBuilder;

  public FilteringResourcesTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
    super(verifierBuilder);
    this.verifierForkedBuilder = verifierBuilder.forkedBuilder();
  }

  private Properties filter(String project) throws Exception {
    File basedir = resources.getBasedir(project);
    MavenExecution verifierBuilder = verifier.forProject(basedir);
    return filter(verifierBuilder);
  }

  private Properties filter(MavenExecution verifierBuilder) throws Exception {
    MavenExecutionResult result = verifierBuilder.execute("process-resources");

    result.assertErrorFreeLog();

    Properties properties = new Properties();
    InputStream is = new FileInputStream(new File(result.getBasedir(), "target/classes/filtered.properties"));
    Reader reader = new InputStreamReader(is, "UTF-8");
    try {
      properties.load(reader);
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
    MavenExecution verifierBuilder = verifier.forProject(basedir).withCliOption("-DcommandLineParameter=value");
    String commandLineParameter = filter(verifierBuilder).getProperty("commandLineParameter");
    Assert.assertEquals("value", commandLineParameter);
  }

  @Test
  public void testSourceEncoding() throws Exception {
    File basedir = resources.getBasedir("filtering-source-encoding");
    // command line -Dfile.encoding=... not work. It must be first params. withCliOptions add it to the end
    // need use JAVA_TOOL_OPTIONS
    Map<String, String> env = new HashMap<>();
    // for test data need encoding windows-1251
    env.put("JAVA_TOOL_OPTIONS", "-Dfile.encoding=windows-1251");
    // default charset cached on first access, so need new process on every test run
    MavenRuntime forkedVerifier = verifierForkedBuilder.withEnvironment(env).build();
    MavenExecution verifierBuilder = forkedVerifier.forProject(basedir);
    Properties props = filter(verifierBuilder);
    String test = props.getProperty("test");
    Assert.assertEquals("'Идентификатор'", test);
  }
}
