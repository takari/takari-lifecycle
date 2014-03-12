package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInputMetadata;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.project.MavenProject;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;

@Named
@MojoExecutionScoped
public class ProjectClasspathDigester {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final String KEY_TYPE = "jdt.type";
  private static final String KEY_HASH = "jdt.hash";
  private static final String KEY_CLASSPATH_DIGEST = "jdt.classpath.digest";

  /**
   * {@code KEY_TYPE} value used for local and anonymous types and also class files with
   * corrupted/unsupported format.
   */
  private static final String TYPE_NOTYPE = ".";

  private final DefaultBuildContext<?> context;

  private final File pom;

  private final ClasspathEntryDigester digester = new ClasspathEntryDigester();

  private final long timestamp;

  @Inject
  public ProjectClasspathDigester(DefaultBuildContext<?> context, MavenProject project,
      MavenSession session) {
    this.context = context;
    this.pom = project.getFile();
    this.timestamp = session.getRequest().getStartTime().getTime();
  }

  /**
   * Returns set of dependency types that changed structurally compared to the previous build,
   * including new and deleted dependency types.
   */
  public Set<String> digestDependencies(List<Artifact> dependencies) throws IOException {
    log.info("Analyzing {} classpath dependencies", dependencies.size());

    Stopwatch stopwatch = new Stopwatch().start();

    ArrayListMultimap<String, byte[]> newIndex = ArrayListMultimap.create();

    for (Artifact dependency : dependencies) {
      addToTypeIndex(newIndex, dependency);
    }

    // pom.xml represent overall process classpath
    DefaultInputMetadata<File> pomMetadata = context.registerInput(pom);
    String oldIndexStr = pomMetadata.getValue(KEY_CLASSPATH_DIGEST, String.class);
    Multimap<String, byte[]> oldIndex = ClasspathEntryDigester.parseTypeIndex(oldIndexStr);
    pomMetadata.process().setValue(KEY_CLASSPATH_DIGEST, ClasspathEntryDigester.toString(newIndex));

    Set<String> result = ClasspathEntryDigester.diff(oldIndex, newIndex);

    log.info("Analyzed {} types in {} classpath dependencies ({} ms)", newIndex.size(),
        dependencies.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));

    return result;
  }

  private void addToTypeIndex(Multimap<String, byte[]> index, Artifact dependency)
      throws IOException {
    File file = dependency.getFile();

    if (!file.exists()) {
      // can happen with multi-module build and empty source directories
      return;
    }

    // three types of dependencies
    // 1. classes folders, always delegate to digester to deal with changes
    // 2. new jars, type index should be fast to read
    // 3. legacy jars, "simple" type index should be fast to generate
    // no pros storing per-dependency type index in BuildContext
    // cons include significant BuildContext state size
    // it makes sense to cache type index in-memory per-jar (or per-dependency)

    index.putAll(digester.readIndex(file, timestamp).getIndex());
  }

  public void writeTypeIndex(File outputDirectory) throws IOException {
    // write type index file
    Multimap<String, byte[]> index = ArrayListMultimap.create();
    for (BuildContext.OutputMetadata<File> output : context.getProcessedOutputs()) {
      String type = output.getValue(KEY_TYPE, String.class);
      if (type == null && output instanceof Output<?>
          && output.getResource().getName().endsWith(".class")) {
        digestClassFile((Output<File>) output, Files.toByteArray(output.getResource()));
        type = output.getValue(KEY_TYPE, String.class);
      }
      if (type != null && !TYPE_NOTYPE.equals(type)) {
        byte[] hash = output.getValue(KEY_HASH, byte[].class);
        index.put(type, hash);
      }
    }
    digester.writeIndex(outputDirectory, index);
  }

  public boolean digestClassFile(Output<File> output, byte[] definition) {
    boolean significantChange = true;
    try {
      ClassFileReader reader =
          new ClassFileReader(definition, output.getResource().getAbsolutePath().toCharArray());
      String type = new String(CharOperation.replaceOnCopy(reader.getName(), '/', '.'));
      byte[] hash = digester.digestClass(reader);
      if (hash != null) {
        output.setValue(KEY_TYPE, type);
        byte[] oldHash = (byte[]) output.setValue(KEY_HASH, hash);
        significantChange = oldHash == null || !Arrays.equals(hash, oldHash);
      } else {
        output.setValue(KEY_TYPE, TYPE_NOTYPE);
      }
    } catch (ClassFormatException e) {
      output.setValue(KEY_TYPE, TYPE_NOTYPE);
    }
    return significantChange;
  }
}
