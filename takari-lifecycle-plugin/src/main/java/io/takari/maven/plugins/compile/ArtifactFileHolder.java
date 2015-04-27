/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.ResourceStatus;
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
