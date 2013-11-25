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
import org.junit.Test;
import org.junit.rules.TestName;

public class BasicTest {
  @Rule
  public final TestName name = new TestName();

  @Test
  public void testBasic() throws Exception {
    File basedir = getBasedir("src/it/basic");
    Verifier verifier = getVerifier(basedir);
    verifier.executeGoal("install"); // TODO deploy
    verifier.verifyErrorFreeLog();

    // TODO assertFileExist, etc
    // TODO assert jar content
    Assert.assertTrue(new File(basedir, "target/basic-1.0.0-SNAPSHOT.jar").canRead());
  }

  private File getBasedir(String path) throws IOException {
    File src = new File(path);
    Assert.assertTrue(path + " is a directory", src.isDirectory());
    File dst = new File("target/it", name.getMethodName());
    FileUtils.deleteDirectory(dst);
    Assert.assertTrue("create target directory", dst.mkdirs());
    FileUtils.copyDirectoryStructure(src, dst);
    return dst;
  }

  private Map<String, String> getTestProperties() throws IOException {
    Properties p = new Properties();
    InputStream os = getClass().getClassLoader().getResourceAsStream("test.properties");
    try {
      p.load(os);
    } finally {
      IOUtil.close(os);
    }
    return new HashMap<String, String>((Map) p);
  }

  private Verifier getVerifier(File basedir) throws VerificationException, IOException {
    File mavenHome = new File("target/dependency/apache-maven-3.1.2-SNAPSHOT");
    Assert.assertTrue("Can't locate maven home, make sure to run 'mvn generate-test-resources'",
        mavenHome.isDirectory());
    // XXX somebody needs to fix this in maven-verifier already
    System.setProperty("maven.home", mavenHome.getAbsolutePath());
    Verifier verifier = new Verifier(basedir.getAbsolutePath());
    verifier.getCliOptions()
        .add("-Dlifecycle-plugin.version=" + getTestProperties().get("version"));
    return verifier;
  }

}
