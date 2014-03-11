package io.takari.maven.plugins.compile;

import java.io.File;
import java.io.Serializable;

class ArtifactFile implements Serializable {

  private static final long serialVersionUID = 1L;

  final File file;

  public ArtifactFile(File file) {
    this.file = file;
  }

  @Override
  public int hashCode() {
    return file.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ArtifactFile)) {
      return false;
    }
    return file.equals(((ArtifactFile) obj).file);
  }
}
