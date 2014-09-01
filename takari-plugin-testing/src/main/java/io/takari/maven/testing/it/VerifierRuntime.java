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

    protected final File classworldsConf;

    protected final List<String> extensions = new ArrayList<>();

    protected final List<String> args = new ArrayList<>();

    VerifierRuntimeBuilder(File mavenHome, File classworldsConf) {
      this.properties = new TestProperties();
      this.mavenHome = mavenHome;
      this.classworldsConf = classworldsConf;

      String workspaceState = System.getProperty(SYSPROP_STATEFILE_LOCATION);
      if (workspaceState == null) {
        workspaceState = properties.get("workspaceStateProperties");
      }
      String workspaceResolver = properties.get("workspaceResolver");
      if (isFile(workspaceState) && isFile(workspaceResolver)) {
        if ("3.2.1".equals(MavenUtils.getMavenVersion(mavenHome, classworldsConf))) {
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
      Embedded3xLauncher launcher = Embedded3xLauncher.createFromMavenHome(mavenHome, classworldsConf, extensions, args);
      return new VerifierRuntime(launcher, properties);
    }
  }

  public static class ForkedVerifierRuntimeBuilder extends VerifierRuntimeBuilder {

    private Map<String, String> environment;

    ForkedVerifierRuntimeBuilder(File mavenHome, File classworldsConf) {
      super(mavenHome, classworldsConf);
    }

    public ForkedVerifierRuntimeBuilder withEnvironment(Map<String, String> environment) {
      this.environment = new HashMap<>(environment);
      return this;
    }

    @Override
    public VerifierRuntime build() {
      ForkedLauncher launcher = new ForkedLauncher(mavenHome, classworldsConf, extensions, environment, args);
      return new VerifierRuntime(launcher, properties);
    }
  }

  VerifierRuntime(MavenLauncher launcher, TestProperties properties) {
    this.launcher = launcher;
    this.properties = properties;
  }

  public static VerifierRuntimeBuilder builder(File mavenHome, File classworldsConf) {
    return new VerifierRuntimeBuilder(mavenHome, classworldsConf);
  }

  public static ForkedVerifierRuntimeBuilder forkedBuilder(File mavenHome) {
    return new ForkedVerifierRuntimeBuilder(mavenHome, null);
  }

  public Verifier forProject(File basedir) {
    return new Verifier(launcher, properties, basedir);
  }
}
