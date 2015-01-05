package io.tesla.maven.plugins.test;

import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.2.3", "3.2.5", "3.2.6-SNAPSHOT"})
public abstract class AbstractIntegrationTest {

  @Rule
  public final TestResources resources = new TestResources();

  public final TestProperties properties = new TestProperties();

  public final MavenRuntime verifier;

  public AbstractIntegrationTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
    this.verifier = verifierBuilder.withCliOptions("-U", "-B").build();
  }

}
