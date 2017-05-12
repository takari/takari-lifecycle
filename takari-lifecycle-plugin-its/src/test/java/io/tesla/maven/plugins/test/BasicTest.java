package io.tesla.maven.plugins.test;

import static io.takari.maven.testing.TestResources.assertFilesPresent;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import io.takari.maven.testing.executor.MavenExecutionResult;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;

public class BasicTest extends AbstractIntegrationTest {

  public BasicTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
    super(verifierBuilder);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("basic");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    File localrepo = properties.getLocalRepository();

    MavenExecutionResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .withCliOption("-X") //
        .execute("deploy");

    result.assertErrorFreeLog();

    // TODO assert expected mojos were executed
    // TODO assertFileExist, etc
    // TODO assert jar content
    Assert.assertTrue(new File(basedir, "target/basic-1.0.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-1.0-tests.jar").canRead());

    File localGroup = new File(localrepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(localGroup, "basic/1.0/basic-1.0.pom").canRead());
    Assert.assertTrue(new File(localGroup, "basic/1.0/basic-1.0.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic/1.0/basic-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic/1.0/basic-1.0-tests.jar").canRead());

    File remoteGroup = new File(remoterepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(remoteGroup, "basic/1.0/basic-1.0.pom").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic/1.0/basic-1.0.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic/1.0/basic-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic/1.0/basic-1.0-tests.jar").canRead());
  }

  @Test
  public void testBasicPlugin() throws Exception {
    File basedir = resources.getBasedir("basic-plugin");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    File localrepo = properties.getLocalRepository();

    MavenExecutionResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .execute("deploy");

    result.assertErrorFreeLog();

    // TODO assert expected mojos were executed
    // TODO assertFileExist, etc
    // TODO assert jar content
    Assert.assertTrue(new File(basedir, "target/basic-plugin-1.0.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-plugin-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-plugin-1.0-tests.jar").canRead());

    File localGroup = new File(localrepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(localGroup, "basic-plugin/1.0/basic-plugin-1.0.pom").canRead());
    Assert.assertTrue(new File(localGroup, "basic-plugin/1.0/basic-plugin-1.0.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic-plugin/1.0/basic-plugin-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic-plugin/1.0/basic-plugin-1.0-tests.jar").canRead());

    File remoteGroup = new File(remoterepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(remoteGroup, "basic-plugin/1.0/basic-plugin-1.0.pom").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic-plugin/1.0/basic-plugin-1.0.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic-plugin/1.0/basic-plugin-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic-plugin/1.0/basic-plugin-1.0-tests.jar").canRead());
  }

  @Test
  public void testBasicBuilder() throws Exception {
    File basedir = resources.getBasedir("basic-builder");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    File localrepo = properties.getLocalRepository();

    MavenExecutionResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .withCliOption("-Dincrementalbuild.version=" + properties.get("incrementalbuild.version")) //
        .execute("deploy");

    result.assertErrorFreeLog();

    // TODO assert expected mojos were executed
    // TODO assertFileExist, etc
    // TODO assert jar content
    Assert.assertTrue(new File(basedir, "target/basic-builder-1.0.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-builder-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-builder-1.0-tests.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/classes/META-INF/m2e/lifecycle-mapping-metadata.xml").canRead());

    File localGroup = new File(localrepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(localGroup, "basic-builder/1.0/basic-builder-1.0.pom").canRead());
    Assert.assertTrue(new File(localGroup, "basic-builder/1.0/basic-builder-1.0.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic-builder/1.0/basic-builder-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic-builder/1.0/basic-builder-1.0-tests.jar").canRead());

    File remoteGroup = new File(remoterepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(remoteGroup, "basic-builder/1.0/basic-builder-1.0.pom").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic-builder/1.0/basic-builder-1.0.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic-builder/1.0/basic-builder-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic-builder/1.0/basic-builder-1.0-tests.jar").canRead());


  }

  @Test
  public void testBasicComponent() throws Exception {
    File basedir = resources.getBasedir("basic-component");

    File remoterepo = new File(basedir, "remoterepo");
    Assert.assertTrue(remoterepo.mkdirs());

    File localrepo = properties.getLocalRepository();

    MavenExecutionResult result = verifier.forProject(basedir) //
        .withCliOption("-Drepopath=" + remoterepo.getCanonicalPath()) //
        .execute("deploy");

    result.assertErrorFreeLog();

    assertFilesPresent(basedir, //
        "target/classes/META-INF/sisu/javax.inject.Named", //
        "target/test-classes/META-INF/sisu/javax.inject.Named", //
        "target/test-classes/test.properties");


    // TODO assert expected mojos were executed
    // TODO assertFileExist, etc
    // TODO assert jar content
    Assert.assertTrue(new File(basedir, "target/basic-component-1.0.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-component-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(basedir, "target/basic-component-1.0-tests.jar").canRead());

    File localGroup = new File(localrepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(localGroup, "basic-component/1.0/basic-component-1.0.pom").canRead());
    Assert.assertTrue(new File(localGroup, "basic-component/1.0/basic-component-1.0.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic-component/1.0/basic-component-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(localGroup, "basic-component/1.0/basic-component-1.0-tests.jar").canRead());

    File remoteGroup = new File(remoterepo, "io/takari/lifecycle/its/basic");
    Assert.assertTrue(new File(remoteGroup, "basic-component/1.0/basic-component-1.0.pom").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic-component/1.0/basic-component-1.0.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic-component/1.0/basic-component-1.0-sources.jar").canRead());
    Assert.assertTrue(new File(remoteGroup, "basic-component/1.0/basic-component-1.0-tests.jar").canRead());
  }
}
