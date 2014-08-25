package io.takari.maven.testing.it;

import static org.eclipse.m2e.workspace.WorkspaceState.SYSPROP_STATEFILE_LOCATION;
import io.takari.maven.testing.TestProperties;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// represents maven installation
public class VerifierRuntime {

  private final MavenLauncher launcher;

  private final TestProperties properties;

  public static class VerifierRuntimeBuilder {

    protected final TestProperties properties;

    protected final File mavenHome;

    protected final List<String> extensions = new ArrayList<>();

    protected final List<String> args = new ArrayList<>();

    VerifierRuntimeBuilder(TestProperties properties, String mavenVersion) {
      this.properties = properties;
      this.mavenHome = MavenUtils.getMavenHome(mavenVersion);

      String workspaceState = System.getProperty(SYSPROP_STATEFILE_LOCATION);
      if (workspaceState == null) {
        workspaceState = properties.get("workspaceStateProperties");
      }
      String workspaceResolver = properties.get("workspaceResolver");
      if (isFile(workspaceState) && isFile(workspaceResolver)) {
        if ("3.2.1".equals(mavenVersion)) {
          throw new IllegalArgumentException("Maven 3.2.1 is not supported, see https://jira.codehaus.org/browse/MNG-5591");
        }
        args.add("-D" + SYSPROP_STATEFILE_LOCATION + "=" + workspaceState);
        extensions.add(workspaceResolver);
      }
      // TODO decide if workspace resolution must be enabled and enforced
    }

    private static boolean isFile(String path) {
      return path != null && new File(path).isFile();
    }

    public VerifierRuntimeBuilder withExtension(File extensionLocation) {
      extensions.add(extensionLocation.getAbsolutePath());
      return this;
    }

    public VerifierRuntimeBuilder withExtensions(Collection<File> extensionLocations) {
      for (File extensionLocation : extensionLocations) {
        extensions.add(extensionLocation.getAbsolutePath());
      }
      return this;
    }

    public VerifierRuntimeBuilder withCliOptions(String... options) {
      for (String option : options) {
        args.add(option);
      }
      return this;
    }

    public VerifierRuntime build() throws Exception {
      Embedded3xLauncher launcher = Embedded3xLauncher.createFromMavenHome(mavenHome, extensions, args);
      return new VerifierRuntime(launcher, properties);
    }
  }

  public static class ForkedVerifierRuntimeBuilder extends VerifierRuntimeBuilder {

    private Map<String, String> environment;

    ForkedVerifierRuntimeBuilder(TestProperties properties, String mavenVersion) {
      super(properties, mavenVersion);
    }

    public ForkedVerifierRuntimeBuilder withEnvironment(Map<String, String> environment) {
      this.environment = new HashMap<>(environment);
      return this;
    }

    @Override
    public VerifierRuntime build() {
      ForkedLauncher launcher = new ForkedLauncher(mavenHome, extensions, environment, args);
      return new VerifierRuntime(launcher, properties);
    }
  }

  VerifierRuntime(MavenLauncher launcher, TestProperties properties) {
    this.launcher = launcher;
    this.properties = properties;
  }

  public static VerifierRuntimeBuilder builder(String mavenVersion) {
    return new VerifierRuntimeBuilder(new TestProperties(), mavenVersion);
  }

  public static ForkedVerifierRuntimeBuilder forkedBuilder(String mavenVersion) {
    return new ForkedVerifierRuntimeBuilder(new TestProperties(), mavenVersion);
  }

  public Verifier forProject(File basedir) {
    return new Verifier(launcher, properties, basedir);
  }
}
