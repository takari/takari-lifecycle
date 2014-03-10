package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.ResourceHolder;

import java.io.File;

// this is a temporary workaround for missing BuildContext API
// what I need, is ability to track source files and dependency artifact files separately
// the proper way to do this is to introduce BuildContext.registerInput(qualifier,file)
// for now, will use ArtifactFile type to separate dependencies and sources
class ArtifactFileHolder implements ResourceHolder<ArtifactFile> {

  private static final long serialVersionUID = 1L;

  private final ArtifactFile file;

  public ArtifactFileHolder(File file) {
    this.file = new ArtifactFile(file);
  }

  @Override
  public ArtifactFile getResource() {
    return file;
  }

  @Override
  public ResourceStatus getStatus() {
    return file.file.isFile() ? ResourceStatus.UNMODIFIED : ResourceStatus.REMOVED;
  }

}
