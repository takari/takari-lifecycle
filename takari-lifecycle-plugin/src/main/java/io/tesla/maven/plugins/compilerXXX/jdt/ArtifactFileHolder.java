package io.tesla.maven.plugins.compilerXXX.jdt;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.ResourceHolder;

import java.io.File;

class ArtifactFileHolder implements ResourceHolder<ArtifactFile> {

  private static final long serialVersionUID = 1L;

  private final ArtifactFile artifactFile;

  private final long lastModified;

  private final long length;

  public ArtifactFileHolder(File file) {
    this.artifactFile = new ArtifactFile(file);
    this.lastModified = file.lastModified();
    this.length = file.length();
  }

  @Override
  public ArtifactFile getResource() {
    return artifactFile;
  }

  @Override
  public ResourceStatus getStatus() {
    File file = artifactFile.file;
    if (file == null) {
      return ResourceStatus.REMOVED;
    }
    if (file.isFile() && lastModified == file.lastModified() && length == file.length()) {
      return ResourceStatus.UNMODIFIED;
    }
    return ResourceStatus.MODIFIED;
  }
}
