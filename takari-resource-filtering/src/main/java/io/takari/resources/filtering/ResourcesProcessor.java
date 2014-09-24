package io.takari.resources.filtering;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.reflect.ReflectionObjectHandler;
import com.github.mustachejava.util.GuardException;
import com.github.mustachejava.util.Wrapper;
import com.google.common.io.ByteStreams;

@Named
@Singleton
public class ResourcesProcessor {

  private final BuildContext buildContext;

  private final DefaultMustacheFactory mustacheFactory;

  @Inject
  public ResourcesProcessor(BuildContext buildContext) {
    this.buildContext = buildContext;
    this.mustacheFactory = new NoEncodingMustacheFactory();
    this.mustacheFactory.setObjectHandler(new MapReflectionObjectHandler());
  }

  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes) throws IOException {
    for (BuildContext.Input<File> input : buildContext.registerAndProcessInputs(sourceDirectory, includes, excludes)) {
      filterResource(input, sourceDirectory, targetDirectory, null);
    }
  }

  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes, Map<Object, Object> filterProperties) throws IOException {
    for (BuildContext.InputMetadata<File> metadata : buildContext.registerInputs(sourceDirectory, includes, excludes)) {
      filterResource(metadata.process(), sourceDirectory, targetDirectory, filterProperties);
    }
  }

  private void filterResource(BuildContext.Input<File> input, File sourceDirectory, File targetDirectory, Map<Object, Object> filterProperties) throws FileNotFoundException, IOException {
    File outputFile = relativize(sourceDirectory, targetDirectory, input.getResource());
    BuildContext.Output<File> output = input.associateOutput(outputFile);
    if (filterProperties != null) {
      try (Reader reader = new FileReader(input.getResource()); Writer writer = new OutputStreamWriter(output.newOutputStream());) {
        // FIXME decide how to handle encoding, system default most likely not it
        filter(reader, writer, filterProperties);
      }
    } else {
      try (InputStream is = new FileInputStream(input.getResource()); OutputStream os = output.newOutputStream()) {
        ByteStreams.copy(is, os);
      }
    }
  }

  private File relativize(File sourceDirectory, File targetDirectory, File sourceFile) {
    String sourceDir = sourceDirectory.getAbsolutePath();
    String source = sourceFile.getAbsolutePath();
    if (!source.startsWith(sourceDir)) {
      throw new IllegalArgumentException(); // can't happen
    }
    String relative = source.substring(sourceDir.length());
    return new File(targetDirectory, relative);
  }

  public void filter(Reader reader, Writer writer, Map<Object, Object> properties) throws IOException {
    Mustache mustache = mustacheFactory.compile(reader, "maven", "${", "}");
    mustache.execute(writer, properties).close();
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
  }

}
