package io.tesla.maven.plugins.test;

import java.io.*;
import java.util.*;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Assert;
import org.junit.Rule;

public abstract class AbstractIntegrationTest {

  @Rule
  public final TestResources resources = new TestResources("src/it", "target/it/");

  protected Map<String, String> getTestProperties() throws IOException {
    Properties p = new Properties();
    InputStream os = getClass().getClassLoader().getResourceAsStream("test.properties");
    try {
      p.load(os);
    } finally {
      IOUtil.close(os);
    }
    return new HashMap<String, String>((Map) p);
  }

  protected Verifier getVerifier(File basedir) throws VerificationException, IOException {
    Map<String, String> testProperties = getTestProperties();
    final File mavenHome =
        new File("target/dependency/apache-maven-" + testProperties.get("mavenVersion"));
    Assert.assertTrue("Can't locate maven home, make sure to run 'mvn generate-test-resources': "
        + mavenHome, mavenHome.isDirectory());
    final File localRepo = new File(testProperties.get("localRepository"));
    Assert.assertTrue("Can't locate maven local repository': " //
        + localRepo, localRepo.isDirectory());
    // XXX somebody needs to fix this in maven-verifier already
    System.setProperty("maven.home", mavenHome.getAbsolutePath());
    Verifier verifier = new Verifier(basedir.getAbsolutePath());
    verifier.getCliOptions().add("-Dlifecycle-plugin.version=" + getPluginVersion());
    verifier.setLocalRepo(localRepo.getCanonicalPath());
    return verifier;
  }

  protected String getPluginVersion() throws IOException {
    return getTestProperties().get("version");
  }

}
