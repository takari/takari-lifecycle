package io.tesla.maven.plugins.test;

import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.it.JUnitParameters;
import io.takari.maven.testing.it.VerifierRuntime;
import io.takari.maven.testing.it.VerifierRuntime.VerifierRuntimeBuilder;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AbstractIntegrationTest {

  @Rule
  public final TestResources resources = new TestResources("src/it", "target/it/");

  public final TestProperties properties;

  public final VerifierRuntime verifier;

  @Parameters(name = "maven-{2}")
  public static Iterable<Object[]> mavenInstallations() throws IOException {
    return JUnitParameters.fromVersions("3.2.2", "3.2.3");
  }

  public AbstractIntegrationTest(File mavenInstallation, File classworldsConf, String version) throws Exception {
    this.properties = new TestProperties();

    VerifierRuntimeBuilder verifierBuilder = VerifierRuntime.builder(mavenInstallation, classworldsConf);
    this.verifier = verifierBuilder.withCliOptions("-U", "-B").build();
  }

}
