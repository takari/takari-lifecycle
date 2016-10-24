/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.resources.filtering;

import java.io.File;

abstract class AbstractResourceProcessor {

  protected static File relativize(File sourceDirectory, File targetDirectory, File sourceFile) {
    String sourceDir = sourceDirectory.getAbsolutePath();
    String source = sourceFile.getAbsolutePath();
    if (!source.startsWith(sourceDir)) {
      throw new IllegalArgumentException(); // can't happen
    }
    String relative = source.substring(sourceDir.length());
    return new File(targetDirectory, relative);
  }

}
