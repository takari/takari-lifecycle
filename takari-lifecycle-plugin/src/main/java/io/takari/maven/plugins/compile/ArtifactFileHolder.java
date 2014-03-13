package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.ResourceHolder;

class ArtifactFileHolder implements ResourceHolder<ArtifactFile> {

  private static final long serialVersionUID = 1L;

  private final ArtifactFile artifact;

  public ArtifactFileHolder(ArtifactFile artifact) {
    this.artifact = artifact;
  }

  @Override
  public ArtifactFile getResource() {
    return artifact;
  }

  @Override
  public ResourceStatus getStatus() {
    // dependency changes are handled with in ProjectClasspathDigester
    return ResourceStatus.UNMODIFIED;
  }
}
