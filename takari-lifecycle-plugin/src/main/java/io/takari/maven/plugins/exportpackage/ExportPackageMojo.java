package io.takari.maven.plugins.exportpackage;

import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultOutput;
import io.takari.maven.plugins.TakariLifecycleMojo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;

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
  private DefaultBuildContext<?> buildContext;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    try {
      generateOutput();
    } catch (IOException e) {
      throw new MojoExecutionException("Could not generate export-package file", e);
    }
  }

  private void generateOutput() throws IOException {
    buildContext.registerAndProcessInputs(classesDirectory, getIncludes(), exportExcludes);
    boolean processingRequired = buildContext.isEscalated();
    Set<String> exportedPackages = new TreeSet<>();
    for (InputMetadata<File> input : buildContext.getRegisteredInputs()) {
      ResourceStatus status = input.getStatus();
      if (status == ResourceStatus.NEW || status == ResourceStatus.REMOVED) {
        processingRequired = true;
      }
      if (status != ResourceStatus.REMOVED) {
        exportedPackages.add(getPackageName(classesDirectory, input.getResource()));
      }
    }
    processingRequired = processingRequired || !outputFile.isFile();
    if (processingRequired) {
      DefaultOutput output = buildContext.processOutput(outputFile);
      try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(output.newOutputStream(), Charsets.UTF_8))) {
        for (String exportedPackage : exportedPackages) {
          w.write(exportedPackage);
          w.write(EOL);
        }
      }
    } else {
      buildContext.markOutputAsUptodate(outputFile);
    }
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
