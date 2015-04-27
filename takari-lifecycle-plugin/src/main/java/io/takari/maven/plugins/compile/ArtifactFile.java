/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile;

import java.io.File;
import java.io.Serializable;

class ArtifactFile implements Serializable {

  private static final long serialVersionUID = 1L;

  final File file;

  final boolean isFile;

  final long length;

  final long lastModified;

  public ArtifactFile(File file, boolean isFile, long length, long lastModified) {
    this.file = file;
    this.isFile = isFile;
    this.length = length;
    this.lastModified = lastModified;
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
    ArtifactFile other = (ArtifactFile) obj;
    return file.equals(other.file);
  }

  @Override
  public String toString() {
    return file.toString();
  }
}
