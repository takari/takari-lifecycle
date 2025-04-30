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
@MavenVersions({"3.6.3", "3.8.8", "3.9.9", "4.0.0-rc-3"})
public abstract class AbstractIntegrationTest {

    // TODO: a hack for MavenInstallationUtils; IT framework should support Maven4
    static {
        System.setProperty("maven.mainClass", "org.apache.maven.cling.MavenCling");
    }

    @Rule
    public final TestResources resources = new TestResources();

    public final TestProperties properties = new TestProperties();

    public final MavenRuntime verifier;

    public AbstractIntegrationTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
        this(verifierBuilder, true);
    }

    public AbstractIntegrationTest(MavenRuntimeBuilder verifierBuilder, boolean forceForked) throws Exception {
        if (forceForked) {
            this.verifier = verifierBuilder
                    .withCliOptions("-U", "-B")
                    .forkedBuilder()
                    // TODO: a hack for MavenRuntime; IT framework should support Maven4
                    .withJvmOption("-Dmaven.mainClass=org.apache.maven.cling.MavenCling")
                    .build();
        } else {
            this.verifier = verifierBuilder.withCliOptions("-U", "-B").build();
        }
    }
}
