package io.takari.maven.plugins.jar;

import java.io.File;

public class ArchiveConfiguration {
  // see http://maven.apache.org/shared/maven-archiver/index.html

  private File manifestFile;

  public File getManifestFile() {
    return manifestFile;
  }
}
