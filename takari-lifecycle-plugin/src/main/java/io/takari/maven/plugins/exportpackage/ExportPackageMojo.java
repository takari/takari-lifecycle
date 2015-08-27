package io.takari.maven.plugins.exportpackage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;

import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputSet;
import io.takari.incrementalbuild.aggregator.MetadataAggregator;
import io.takari.maven.plugins.TakariLifecycleMojo;

@Mojo(name = "export-package", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class ExportPackageMojo extends TakariLifecycleMojo {

  public static final String PATH_EXPORT_PACKAGE = "META-INF/takari/export-package";

  public static final char EOL = '\n';

  @Parameter(defaultValue = "${project.build.outputDirectory}/" + PATH_EXPORT_PACKAGE)
  private File outputFile;

  @Parameter(defaultValue = "${project.build.outputDirectory}")
  private File classesDirectory;

  @Parameter
  private Set<String> exportIncludes = ImmutableSet.of();

  @Parameter
  private Set<String> exportExcludes = ImmutableSet.of("**/internal/**", "**/impl/**");

  @Inject
  private AggregatorBuildContext buildContext;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    try {
      classesDirectory = classesDirectory.getCanonicalFile();
      generateOutput();
    } catch (IOException e) {
      throw new MojoExecutionException("Could not generate export-package file", e);
    }
  }

  private void generateOutput() throws IOException {
    InputSet output = buildContext.newInputSet();
    output.addInputs(classesDirectory, getIncludes(), exportExcludes);
    output.aggregateIfNecessary(outputFile, new MetadataAggregator<String>() {
      @Override
      public Map<String, String> glean(File input) throws IOException {
        return Collections.singletonMap(getPackageName(classesDirectory, input), null);
      }

      @Override
      public void aggregate(Output<File> output, Map<String, String> metadata) throws IOException {
        Set<String> exportedPackages = new TreeSet<>(metadata.keySet());
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(output.newOutputStream(), Charsets.UTF_8))) {
          for (String exportedPackage : exportedPackages) {
            w.write(exportedPackage);
            w.write(EOL);
          }
        }
      }
    });
  }

  private Collection<String> getIncludes() {
    if (exportIncludes.isEmpty()) {
      return ImmutableSet.of("**/*.class");
    }
    Set<String> includes = new HashSet<>();
    for (String include : this.exportIncludes) {
      include = include.replace('\\', '/');
      if (!include.endsWith("/")) {
        include = include + "/";
      }
      include = include + "*.class";
    }
    return includes;
  }

  static String getPackageName(File basedir, File file) {
    String relpath = basedir.toPath().relativize(file.getParentFile().toPath()).toString();
    return relpath.replace('\\', '/').replace('/', '.');
  }

}
