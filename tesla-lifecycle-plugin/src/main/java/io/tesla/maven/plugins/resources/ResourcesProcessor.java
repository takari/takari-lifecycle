package io.tesla.maven.plugins.resources;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.tesla.incremental.BuildContext;
import org.eclipse.tesla.incremental.DefaultFileSet;
import org.eclipse.tesla.incremental.FileSetBuilder;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
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
  }
    
  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes) throws IOException {
    process(sourceDirectory, targetDirectory, includes, excludes, null);
  }
    
  public void process(File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes, Properties filterProperties) throws IOException {
    if (includes.isEmpty()) {
      includes.add("**/**");
    }
    DefaultFileSet fileSet = new FileSetBuilder(sourceDirectory) //
        .addIncludes(includes) //
        .addExcludes(excludes) //
        .build();
    for (File inputFile : buildContext.registerInputs(fileSet)) {
      buildContext.addProcessedInput(inputFile);
      File outputFile = fileSet.relativize(targetDirectory, inputFile);
      Closer closer = Closer.create();
      try {
        Reader reader = closer.register(new FileReader(inputFile));
        Writer writer = closer.register(new OutputStreamWriter(buildContext.newOutputStream(inputFile, outputFile)));
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
  
  public void filter(Reader reader, Writer writer, Map<Object,Object> properties) throws IOException {
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
}
