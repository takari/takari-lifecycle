package io.takari.maven.testing.it;

import io.takari.maven.testing.TestProperties;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// represents maven invocation parameters
public class Verifier {

  private final MavenLauncher launcher;

  private final TestProperties properties;

  private final File basedir;

  private final List<String> cliOptions = new ArrayList<>();

  Verifier(MavenLauncher launcher, TestProperties properties, File basedir) {
    this.launcher = launcher;
    this.properties = properties;
    this.basedir = basedir;
  }

  public VerifierResult execute(String... goals) throws Exception {
    File logFile = new File(basedir, "log.txt");

    List<String> args = new ArrayList<>();

    File userSettings = properties.getUserSettings();
    if (userSettings != null && userSettings.isFile()) {
      args.add("-s");
      args.add(userSettings.getAbsolutePath());
    }
    args.add("-Dmaven.repo.local=" + properties.getLocalRepository().getAbsolutePath());
    args.add("-Dit-plugin.version=" + properties.getPluginVersion());
    args.addAll(cliOptions);

    for (String goal : goals) {
      args.add(goal);
    }

    launcher.run(args.toArray(new String[args.size()]), basedir.getAbsolutePath(), logFile);

    return new VerifierResult(basedir, logFile);
  }

  public Verifier withCliOption(String string) {
    cliOptions.add(string);
    return this;
  }
}
