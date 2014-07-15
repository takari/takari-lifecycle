package io.tesla.maven.plugins.test;

import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.it.VerifierRuntime;
import io.takari.maven.testing.it.VerifierRuntime.VerifierRuntimeBuilder;

import java.util.Arrays;

import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AbstractIntegrationTest {

  public final String mavenVersion;

  @Rule
  public final TestResources resources = new TestResources("src/it", "target/it/");

  public final TestProperties properties;

  public final VerifierRuntime verifier;

  @Parameters(name = "maven-{0}")
  public static Iterable<Object[]> mavenVersions() {
    return Arrays.<Object[]>asList( //
        new Object[] {"3.2.1"} //
        , new Object[] {"3.2.2"} //
        );
  }

  public AbstractIntegrationTest(String mavenVersion) throws Exception {
    this.mavenVersion = mavenVersion;
    this.properties = new TestProperties();

    VerifierRuntimeBuilder verifierBuilder = VerifierRuntime.builder(mavenVersion);
    this.verifier = verifierBuilder.build();
  }

}
