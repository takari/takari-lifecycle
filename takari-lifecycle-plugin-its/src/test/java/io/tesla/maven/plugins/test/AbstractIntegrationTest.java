package io.tesla.maven.plugins.test;

import org.junit.Rule;
import org.junit.runner.RunWith;

import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public abstract class AbstractIntegrationTest {

  @Rule
  public final TestResources resources = new TestResources();

  public final TestProperties properties = new TestProperties();

  public final MavenRuntime verifier;

  public AbstractIntegrationTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
    this.verifier = verifierBuilder.withCliOptions("-U", "-B").build();
  }

}
