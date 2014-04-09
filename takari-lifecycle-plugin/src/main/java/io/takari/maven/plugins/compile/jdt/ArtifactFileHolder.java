package io.takari.maven.plugins.compile.jdt;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.ResourceHolder;

import java.io.File;

class ArtifactFileHolder implements ResourceHolder<ArtifactFile> {

  private static final long serialVersionUID = 1L;

  private final ArtifactFile artifact;

  private final long length;

  private final long lastModified;

  public ArtifactFileHolder(File file) {
    this.artifact = new ArtifactFile(file);
    this.length = file.length();
    this.lastModified = file.lastModified();
  }

  @Override
  public ArtifactFile getResource() {
    return artifact;
  }

  @Override
  public ResourceStatus getStatus() {
    final File file = artifact.file;
    if (file.isFile()) {
      return lastModified == file.lastModified() && length == file.length()
          ? ResourceStatus.UNMODIFIED
          : ResourceStatus.MODIFIED;
    } else if (file.isDirectory()) {
      // dependency changes are handled with in ProjectClasspathDigester
      return ResourceStatus.UNMODIFIED;
    }
    return ResourceStatus.REMOVED;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ArtifactFileHolder)) {
      return false;
    }
    return artifact.equals(((ArtifactFileHolder) obj).artifact);
  }
}
