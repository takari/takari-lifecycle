/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.resources.filtering;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import io.takari.incrementalbuild.BuildContext;

@Named
@Singleton
public class ResourcesProcessor {

  private final CopyResourcesProcessor copyProcessor;
  private final FilterResourcesProcessor filterProcessor;

  @Inject
  public ResourcesProcessor(BuildContext buildContext) {
    this.copyProcessor = new CopyResourcesProcessor(buildContext);
    this.filterProcessor = new FilterResourcesProcessor(buildContext);
  }

  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes, String encoding) throws IOException {
    copyProcessor.process(sourceDirectory, targetDirectory, includes, excludes, encoding);
  }

  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes, Map<Object, Object> filterProperties, String encoding) throws IOException {
    filterProcessor.process(sourceDirectory, targetDirectory, includes, excludes, filterProperties, Collections.emptyList(), encoding);
  }

  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes, Map<Object, Object> filterProperties, List<File> filters, String encoding)
      throws IOException {
    filterProcessor.process(sourceDirectory, targetDirectory, includes, excludes, filterProperties, filters, encoding);
  }

  public void filter(Reader reader, Writer writer, Map<Object, Object> properties) throws IOException {
    filterProcessor.filter(reader, writer, properties);
  }


}
