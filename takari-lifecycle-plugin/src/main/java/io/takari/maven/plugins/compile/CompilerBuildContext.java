package io.takari.maven.plugins.compile;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.project.MavenProject;

import io.takari.incrementalbuild.MessageSeverity;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.spi.AbstractBuildContext;
import io.takari.incrementalbuild.spi.BuildContextEnvironment;
import io.takari.incrementalbuild.spi.DefaultBuildContextState;
import io.takari.incrementalbuild.spi.DefaultOutput;
import io.takari.incrementalbuild.spi.DefaultResource;
import io.takari.incrementalbuild.spi.DefaultResourceMetadata;


// TODO replace all Default* implementation types with corresponding API interfaces
@Named
@MojoExecutionScoped
public class CompilerBuildContext extends AbstractBuildContext {

  private final File pom;

  @Inject
  public CompilerBuildContext(BuildContextEnvironment configuration, MavenProject project) {
    super(configuration);
    pom = project.getFile();
  }

  //
  // overall build context management
  //

  @Override
  public void markSkipExecution() {
    super.markSkipExecution();
  }

  /**
   * Marks current build as up-to-date. All input and output resources and their corresponding metadata are carried-over to the next build as-is. All messages produced during previous build are
   * replayed.
   */
  public void markUptodateExecution() {
    // TODO enforce context hasn't been modified yet.

    // copy output ResourceHolders from previous build. this has the same effect as input registration.
    // the outputs will be considered up-to-date and all their metadata will be carried-over as-is
    for (File outputFile : oldState.getOutputs()) {
      markUptodateOutput(outputFile);
    }
  }

  @Override
  public void markUptodateOutput(File outputFile) {
    super.markUptodateOutput(outputFile);
  }

  /**
   * Adds messages associated with mojo execution in project pom.xml. This is useful to capture compiler failures, i.e. exception in the compiler itself. These messages are carried-over during
   * no-changed rebuild and trigger build failures as expected. These messages are discarded during full or incremental build and must be recreated as needed.
   */
  public void addPomMessage(String message, MessageSeverity severity, Throwable cause) {
    // TODO execution line/column
    super.addMessage(pom, 0, 0, message, severity, cause);
  }

  @Override
  public boolean isEscalated() {
    return super.isEscalated();
  }

  /**
   * Returns attribute value set at context-level, i.e. not associated with any particular resource, during previous build.
   */
  public <V extends Serializable> V getAttribute(String key, boolean previous, Class<V> clazz) {
    return super.getResourceAttribute(oldState, pom, key, clazz);
  }

  /**
   * Sets context-level, i.e. not associated with any particular resource, key/value attribute. Context-level attributes are not automatically carried-over from one build to the next and must be
   * explicitly set during each build.
   */
  public <V extends Serializable> Serializable setAttribute(String key, V value) {
    return super.setResourceAttribute(pom, key, value);
  }

  //
  // sources tracking
  //

  public Collection<DefaultResourceMetadata<File>> registerSources(File basedir, Collection<String> includes, Collection<String> excludes) throws IOException {
    return super.registerInputs(basedir, includes, excludes);
  }

  /**
   * Returns sources removed since previous build. Does not return generated sources.
   */
  public Collection<ResourceMetadata<File>> getRemovedSources() {
    Collection<ResourceMetadata<File>> sources = new ArrayList<>();
    for (Object resource : oldState.getResources().keySet()) {
      if (isJavaSource(resource) && !oldState.isOutput(resource) && !isRegisteredResource(resource)) {
        sources.add(newResourceMetadata(oldState, (File) resource));
      }
    }
    return sources;
  }

  /**
   * Returns original or generated source processed during this build. Throws {@link IllegalStateException} if no such source.
   */
  public Resource<File> getProcessedSource(File sourceFile) {
    if (!isProcessedResource(sourceFile) || !isJavaSource(sourceFile)) {
      // JDT may decide to compile more sources than it was asked to in some cases
      // TODO investigate when this happens and decide what to do about it
      throw new IllegalArgumentException();
    }
    return newResource(sourceFile);
  }

  /**
   * Returns sources registered during this build.
   */
  public Collection<ResourceMetadata<File>> getRegisteredSources() {
    List<ResourceMetadata<File>> sources = new ArrayList<>();
    for (Object resource : state.getResources().keySet()) {
      if (isJavaSource(resource)) {
        DefaultBuildContextState state = isProcessedResource(resource) ? this.state : this.oldState;
        sources.add(newResourceMetadata(state, (File) resource));
      }
    }
    return sources;
  }

  public static boolean isJavaSource(Object resource) {
    return resource instanceof File && ((File) resource).getName().endsWith(".java"); // TODO find proper constant
  }

  public <V extends Serializable> V getAttribute(File source, String key, Class<V> clazz) {
    return getResourceAttribute(getState(source), source, key, clazz);
  }

  private DefaultBuildContextState getState(File source) {
    return isProcessedResource(source) ? this.state : this.oldState;
  }

  public <V extends Serializable> Serializable setAttribute(File source, String key, V value) {
    return setResourceAttribute(source, key, value);
  }

  //
  // output tracking
  //

  /**
   * Deletes all outputs registered with the build context
   */
  public Collection<ResourceMetadata<File>> deleteOutputs() throws IOException {
    List<ResourceMetadata<File>> deleted = new ArrayList<>();
    for (File outputFile : oldState.getOutputs()) {
      deleteOutput(outputFile);
      deleted.add(newResourceMetadata(oldState, outputFile));
    }
    return deleted;
  }

  /**
   * Returns outputs directly or indirectly derived from the source.
   */
  public Collection<ResourceMetadata<File>> getAssociatedOutputs(ResourceMetadata<File> source) {
    return addAssociatedOutputs(new HashMap<File, ResourceMetadata<File>>(), source).values();
  }

  @Override
  protected Collection<? extends ResourceMetadata<File>> getAssociatedOutputs(DefaultBuildContextState state, Object resource) {
    return super.getAssociatedOutputs(state, resource);
  }

  @SuppressWarnings("unchecked")
  public Collection<ResourceMetadata<File>> getAssociatedOutputs(File source) {
    return (Collection<ResourceMetadata<File>>) super.getAssociatedOutputs(getState(source), source);
  }

  private Map<File, ResourceMetadata<File>> addAssociatedOutputs(Map<File, ResourceMetadata<File>> outputs, ResourceMetadata<File> resource) {
    for (ResourceMetadata<File> output : super.getAssociatedOutputs(getState(resource.getResource()), resource.getResource())) {
      if (!outputs.containsKey(output.getResource())) {
        outputs.put(output.getResource(), output);
        addAssociatedOutputs(outputs, output);
      }
    }
    return outputs;
  }

  @Override
  public DefaultOutput processOutput(File outputFile) {
    return super.processOutput(outputFile);
  }

  /**
   * This method is similar to ResourceMetadata.process(), but discards state associated with the inputResource during each invocation. Useful when recompiling the same source multiple times during
   * incremental build iterations.
   */
  public Resource<File> processInput(ResourceMetadata<File> inputResource) {
    super.processResource(inputResource.getResource());
    return inputResource.process();
  }

  @Override
  public void deleteOutput(File outputFile) throws IOException {
    super.deleteOutput(outputFile);
  }

  public boolean isProcessedOutput(File outputFile) {
    return state.isOutput(outputFile) && isProcessedResource(outputFile);
  }

  public Output<File> associatedOutput(Resource<File> input, File outputFile) {
    return input.associateOutput(outputFile);
  }

  //
  // context commit
  //

  @Override
  protected void assertAssociation(DefaultResource<?> resource, DefaultOutput output) {
    // allow any input/output association
  }

  @Override
  protected void finalizeContext() {
    for (Object resource : oldState.getResources().keySet()) {
      if (isProcessedResource(resource) || isDeletedResource(resource)) {
        // known deleted or processed resource, nothing to carry over
        continue;
      }

      if (!oldState.isOutput(resource) && !state.isResource(resource)) {
        // deleted or excluded source, nothing to carry over
        continue;
      }

      state.putResource(resource, oldState.getResource(resource));
      if (oldState.isOutput(resource)) {
        state.addOutput((File) resource);
      }
      state.setResourceMessages(resource, oldState.getResourceMessages(resource));
      state.setResourceAttributes(resource, oldState.getResourceAttributes(resource));
      state.setResourceOutputs(resource, oldState.getResourceOutputs(resource));

      // XXX inputs and outputs, which ones do we need here?
    }
  }
}
