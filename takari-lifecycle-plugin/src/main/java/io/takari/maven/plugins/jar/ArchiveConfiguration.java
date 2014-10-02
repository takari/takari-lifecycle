/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import java.io.File;

public class ArchiveConfiguration {
  // see http://maven.apache.org/shared/maven-archiver/index.html

  private File manifestFile;

  public File getManifestFile() {
    return manifestFile;
  }
}
