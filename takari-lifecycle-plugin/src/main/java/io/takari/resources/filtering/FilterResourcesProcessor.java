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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.reflect.ReflectionObjectHandler;
import com.github.mustachejava.util.GuardException;
import com.github.mustachejava.util.Wrapper;
import com.google.common.collect.Maps;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;
import io.takari.incrementalbuild.ResourceMetadata;

class FilterResourcesProcessor extends AbstractResourceProcessor {

  private final BuildContext buildContext;

  private final DefaultMustacheFactory mustacheFactory;

  public FilterResourcesProcessor(BuildContext buildContext) {
    this.buildContext = buildContext;
    this.mustacheFactory = new NoEncodingMustacheFactory();
    this.mustacheFactory.setObjectHandler(new MapReflectionObjectHandler());
  }

  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes, Map<Object, Object> filterProperties, List<File> filters, String encoding)
      throws IOException {
    Map<Object, Object> effectiveProperties = new HashMap<>(filterProperties);
    for (File filter : filters) {
      readProperties(filter).forEach((key, value) -> {
        if (!effectiveProperties.containsKey(key)) {
          effectiveProperties.put(key, value);
        }
      });
    }
    for (ResourceMetadata<File> metadata : buildContext.registerInputs(sourceDirectory, includes, excludes)) {
      filterResource(metadata.process(), sourceDirectory, targetDirectory, effectiveProperties, encoding);
    }
  }

  private static Map<String, String> readProperties(File file) {
    Properties properties = new Properties();
    try (InputStream is = new FileInputStream(file)) {
      properties.load(is);
    } catch (IOException e) {
      // too bad
    }
    return Maps.fromProperties(properties);
  }

  private void filterResource(Resource<File> input, File sourceDirectory, File targetDirectory, Map<Object, Object> filterProperties, String encoding) throws IOException {
    File outputFile = relativize(sourceDirectory, targetDirectory, input.getResource());
    Output<File> output = input.associateOutput(outputFile);
    try (Reader reader = newReader(input, encoding); Writer writer = newWriter(output, encoding)) {
      filter(reader, writer, filterProperties);
    }
  }

  public void filter(Reader reader, Writer writer, Map<Object, Object> properties) throws IOException {
    Mustache mustache = mustacheFactory.compile(reader, "maven", "${", "}");
    mustache.execute(writer, properties).close();
  }

  private Reader newReader(Resource<File> resource, String encoding) throws IOException {
    if (encoding == null) {
      return new FileReader(resource.getResource());
    } else {
      return new InputStreamReader(new FileInputStream(resource.getResource()), encoding);
    }
  }

  private Writer newWriter(Output<File> output, String encoding) throws IOException {
    if (encoding == null) {
      return new OutputStreamWriter(output.newOutputStream());
    } else {
      return new OutputStreamWriter(output.newOutputStream(), encoding);
    }
  }

  private static class NoEncodingMustacheFactory extends DefaultMustacheFactory {
    @Override
    public void encode(String value, Writer writer) {
      //
      // TODO: encoding rules
      //
      try {
        writer.write(value);
      } catch (IOException e) {
        throw new MustacheException(e);
      }
    }
  }

  // workaround for https://github.com/spullara/mustache.java/issues/92
  // performs full-name map lookup before falling back to dot-notation parsing
  private static class MapReflectionObjectHandler extends ReflectionObjectHandler {
    @Override
    public Wrapper find(final String name, Object[] scopes) {
      for (final Object scope : scopes) {
        if (scope instanceof Map && ((Map) scope).containsKey(name)) {
          return new Wrapper() {
            @Override
            public Object call(Object[] scopes) throws GuardException {
              return ((Map) scope).get(name);
            }
          };
        }
      }
      return super.find(name, scopes);
    }

    @Override
    public String stringify(Object object) {
      if (object instanceof File) {
        return object.toString().replace('\\', '/'); // I <3 Windows
      }
      return object.toString();
    }

  }

}
