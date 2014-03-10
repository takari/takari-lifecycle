package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInputMetadata;

import java.io.*;
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
import com.google.common.collect.*;
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
    Stopwatch stopwatch = new Stopwatch().start();

    ArrayListMultimap<String, byte[]> newIndex = ArrayListMultimap.create();

    for (Artifact dependency : dependencies) {
      addToTypeIndex(newIndex, dependency);
    }

    // pom.xml represent overall process classpath
    DefaultInputMetadata<File> pomMetadata = context.registerInput(pom);
    Multimap<String, byte[]> oldIndex = getTypeIndex(pomMetadata);
    pomMetadata.process().setValue(KEY_CLASSPATH_DIGEST, newIndex);

    Set<String> result = ClasspathEntryDigester.diff(oldIndex, newIndex);

    log.info("type index artifacts {} types {} {} ms", dependencies.size(), newIndex.size(),
        stopwatch.elapsed(TimeUnit.MILLISECONDS));

    return result;
  }

  private void addToTypeIndex(Multimap<String, byte[]> index, Artifact dependency)
      throws IOException {
    DefaultInputMetadata<ArtifactFile> metadata = null;
    Multimap<String, byte[]> typeIndex = null;

    File file = dependency.getFile();
    if (file.isFile()) {
      // XXX introduce #registerInput(qualifier, File);
      metadata = context.registerInput(new ArtifactFileHolder(file));
      typeIndex = getTypeIndex(metadata);
    }

    if (typeIndex == null) {
      typeIndex = digester.readIndex(file, timestamp).getIndex();
    }

    if (metadata != null) {
      // XXX do this for legacy dependency jars only
      // for modern jars this only wastes context state
      metadata.process().setValue(KEY_CLASSPATH_DIGEST, ImmutableMultimap.copyOf(typeIndex));
    }

    index.putAll(typeIndex);
  }

  @SuppressWarnings("unchecked")
  private Multimap<String, byte[]> getTypeIndex(DefaultInputMetadata<?> dependency) {
    return (Multimap<String, byte[]>) dependency.getValue(KEY_CLASSPATH_DIGEST, Serializable.class);
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
