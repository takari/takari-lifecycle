package io.takari.maven.plugins.resources;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
import com.google.common.io.CharStreams;
import com.google.common.io.Closer;

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
    process(sourceDirectory, targetDirectory, includes, excludes, null);
  }

  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes, Map<Object, Object> filterProperties) throws IOException {
    if (!sourceDirectory.isDirectory()) {
      // DirectoryScanner chokes on directories that do not exist
      return;
    }
    for (BuildContext.Input<File> input : buildContext.registerAndProcessInputs(sourceDirectory, includes, excludes)) {
      File outputFile = relativize(sourceDirectory, targetDirectory, input.getResource());
      BuildContext.Output<File> output = input.associateOutput(outputFile);
      Closer closer = Closer.create();
      try {
        // FIXME decide how to handle encoding, system default most likely not it
        Reader reader = closer.register(new FileReader(input.getResource()));
        Writer writer = closer.register(new OutputStreamWriter(output.newOutputStream()));
        if (filterProperties != null) {
          filter(reader, writer, filterProperties);
        } else {
          CharStreams.copy(reader, writer);
        }
      } finally {
        closer.close();
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
