package io.tesla.maven.plugins.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AbstractIntegrationTest {

  private final String mavenVersion;

  @Rule
  public final TestResources resources = new TestResources("src/it", "target/it/");

  @Parameters(name = "maven-{0}")
  public static Iterable<Object[]> mavenVersions() {
    return Arrays.<Object[]>asList( //
        new Object[] {"3.2.1"} //
        , new Object[] {"3.2.2"} //
        );
  }

  public AbstractIntegrationTest(String mavenVersion) {
    this.mavenVersion = mavenVersion;
  }

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
    final File mavenHome = new File("target/dependency/apache-maven-" + mavenVersion);
    Assert.assertTrue("Can't locate maven home, make sure to run 'mvn generate-test-resources': "
        + mavenHome, mavenHome.isDirectory());
    final File localRepo = new File(testProperties.get("localRepository"));
    Assert.assertTrue("Can't locate maven local repository': " //
        + localRepo, localRepo.isDirectory());
    final File userSettingsFile = new File(testProperties.get("userSettingsFile"));
    // XXX somebody needs to fix this in maven-verifier already
    System.setProperty("maven.home", mavenHome.getCanonicalPath());
    Verifier verifier = new Verifier(basedir.getCanonicalPath());
    verifier.getCliOptions().add("-Dlifecycle-plugin.version=" + getPluginVersion());
    verifier.setLocalRepo(localRepo.getCanonicalPath());
    if (userSettingsFile != null && userSettingsFile.isFile()) {
      verifier.getCliOptions().add("-s");
      verifier.getCliOptions().add(userSettingsFile.getCanonicalPath());
    }
    verifier.getCliOptions().add("-U");
    return verifier;
  }

  protected String getPluginVersion() throws IOException {
    return getTestProperties().get("version");
  }

}
