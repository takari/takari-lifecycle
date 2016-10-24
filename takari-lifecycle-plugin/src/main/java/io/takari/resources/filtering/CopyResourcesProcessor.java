/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.resources.filtering;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import com.google.common.io.ByteStreams;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;

class CopyResourcesProcessor extends AbstractResourceProcessor {

  private final BuildContext buildContext;

  public CopyResourcesProcessor(BuildContext buildContext) {
    this.buildContext = buildContext;
  }

  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes, String encoding) throws IOException {
    for (Resource<File> input : buildContext.registerAndProcessInputs(sourceDirectory, includes, excludes)) {
      copyResource(input, sourceDirectory, targetDirectory, null, encoding);
    }
  }

  private void copyResource(Resource<File> input, File sourceDirectory, File targetDirectory, Map<Object, Object> filterProperties, String encoding) throws IOException {
    File outputFile = relativize(sourceDirectory, targetDirectory, input.getResource());
    Output<File> output = input.associateOutput(outputFile);
    try (InputStream is = new FileInputStream(input.getResource()); OutputStream os = output.newOutputStream()) {
      ByteStreams.copy(is, os);
    }
  }

}
