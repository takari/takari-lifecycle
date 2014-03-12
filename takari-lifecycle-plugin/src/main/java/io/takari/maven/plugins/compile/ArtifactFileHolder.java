package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.ResourceHolder;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.plexus.util.DirectoryScanner;

public class ArtifactFileHolder implements ResourceHolder<ArtifactFile> {

  private static final long serialVersionUID = 1L;

  // this assumes single classloader
  private static final transient Map<File, ArtifactFile> CACHE =
      new ConcurrentHashMap<File, ArtifactFile>();

  private final ArtifactFile artifact;

  public ArtifactFileHolder(File file) {
    this.artifact = newArtifactFile(file);
    if (artifact == null) {
      throw new IllegalArgumentException("Path does not exist " + file);
    }
  }

  @Override
  public ArtifactFile getResource() {
    return artifact;
  }

  @Override
  public ResourceStatus getStatus() {
    ArtifactFile current = newArtifactFile(this.artifact.file);
    if (current == null) {
      return ResourceStatus.REMOVED;
    }
    if (current.isFile != artifact.isFile || current.length != artifact.length
        || current.lastModified != artifact.lastModified) {
      return ResourceStatus.MODIFIED;
    }
    return ResourceStatus.UNMODIFIED;
  }

  private static ArtifactFile newArtifactFile(File resource) {
    ArtifactFile artifact = CACHE.get(resource);
    if (artifact == null) {
      if (resource.isFile()) {
        artifact = new ArtifactFile(resource, true, resource.length(), resource.lastModified());
      } else if (resource.isDirectory()) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(resource);
        scanner.scan();
        long lastModified = 0, length = 0;
        for (String path : scanner.getIncludedFiles()) {
          lastModified = Math.max(lastModified, new File(resource, path).lastModified());
          length++;
        }
        artifact = new ArtifactFile(resource, false, length, lastModified);
      } // else resource does not exist
      CACHE.put(resource, artifact);
    }
    return artifact;
  }

  // XXX decide if there is a way to get rid of this
  // current, required by tests for incremental changes to reactor project
  public static void flushCache() {
    CACHE.clear();
  }
}
