package io.takari.maven.plugins.sisu;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.IBinaryAnnotation;

import com.google.common.base.Charsets;

import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputSet;
import io.takari.incrementalbuild.aggregator.MetadataAggregator;
import io.takari.maven.plugins.TakariLifecycleMojo;

abstract class AbstractSisuIndexMojo extends TakariLifecycleMojo {
  @Component
  private AggregatorBuildContext context;

  @Override
  protected void executeMojo() throws MojoExecutionException {

    try {
      InputSet inputs = context.newInputSet();
      inputs.addInputs(getOutputDirectory(), Collections.singleton("**/*.class"), null);
      inputs.aggregateIfNecessary(getOutputFile(), new MetadataAggregator<String>() {
        @Override
        public Map<String, String> glean(File input) throws IOException {
          return gleanNamedType(input);
        }

        @Override
        public void aggregate(Output<File> output, Map<String, String> metadata) throws IOException {
          writeIndex(output, metadata.keySet());
        }
      });
    } catch (IOException e) {
      throw new MojoExecutionException("Could not create sisu index " + getOutputFile(), e);
    }
  }

  void writeIndex(Output<File> output, Set<String> types) throws IOException {
    TreeSet<String> sorted = new TreeSet<>(types);
    try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(output.newOutputStream(), Charsets.UTF_8))) {
      for (String type : sorted) {
        w.write(type);
        w.newLine();
      }
    }
  }

  Map<String, String> gleanNamedType(File classfile) throws IOException {
    // use jdt class reader to avoid extra runtime dependency, otherwise could use asm

    try {
      ClassFileReader type = ClassFileReader.read(classfile);
      IBinaryAnnotation[] annotations = type.getAnnotations();
      if (annotations != null) {
        for (IBinaryAnnotation annotation : annotations) {
          if ("Ljavax/inject/Named;".equals(new String(annotation.getTypeName()))) {
            return Collections.singletonMap(new String(type.getName()).replace('/', '.'), null);
          }
        }
      }
    } catch (ClassFormatException e) {
      // silently ignore classes we can't read/parse
    }

    return null;
  }

  protected abstract File getOutputDirectory();

  protected abstract File getOutputFile();
}
