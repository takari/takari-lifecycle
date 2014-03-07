package io.takari.maven.plugins.compiler.incremental;

import java.io.File;
import java.io.Serializable;

class ArtifactFile implements Serializable {

  private static final long serialVersionUID = 1L;

  final File file;

  public ArtifactFile(File file) {
    this.file = file;
  }

}
