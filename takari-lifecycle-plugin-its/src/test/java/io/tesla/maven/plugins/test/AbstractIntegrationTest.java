package io.tesla.maven.plugins.test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.TestName;

public abstract class AbstractIntegrationTest {

  @Rule
  public final TestName name = new TestName();

  protected File getBasedir(String path) throws IOException {
    File src = new File(path);
    Assert.assertTrue(path + " is a directory", src.isDirectory());
    File dst = new File("target/it", getClass().getSimpleName() + "-" + name.getMethodName());
    FileUtils.deleteDirectory(dst);
    Assert.assertTrue("create target directory", dst.mkdirs());
    FileUtils.copyDirectoryStructure(src, dst);
    return dst;
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
    File mavenHome =
        new File("target/dependency/apache-maven-" + getTestProperties().get("mavenVersion"));
    Assert.assertTrue("Can't locate maven home, make sure to run 'mvn generate-test-resources': "
        + mavenHome, mavenHome.isDirectory());
    // XXX somebody needs to fix this in maven-verifier already
    System.setProperty("maven.home", mavenHome.getAbsolutePath());
    Verifier verifier = new Verifier(basedir.getAbsolutePath());
    verifier.getCliOptions().add("-Dlifecycle-plugin.version=" + getPluginVersion());
    return verifier;
  }

  protected String getPluginVersion() throws IOException {
    return getTestProperties().get("version");
  }

}
