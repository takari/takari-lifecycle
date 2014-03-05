package io.tesla.maven.plugins.compilerXXX;

import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.tesla.maven.plugins.compilerXXX.jdt.IncrementalCompiler;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractCompileMojo extends AbstractMojo
    implements
      InternalCompilerConfiguration {
  @Inject
  private Provider<DefaultBuildContext<?>> context;

  /**
   * The compiler id of the compiler to use, {@code javac} or {@code incremental-jdt}.
   */
  @Parameter(property = "maven.compiler.compilerId", defaultValue = "incremental-jdt")
  private String compilerId;

  /**
   * The -source argument for the Java compiler.
   */
  @Parameter(property = "maven.compiler.source", defaultValue = "1.5")
  protected String source;

  /**
   * The -target argument for the Java compiler.
   */
  @Parameter(property = "maven.compiler.target", defaultValue = "1.5")
  protected String target;

  @Parameter(defaultValue = "${project.file}", readonly = true)
  protected File pom;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if ("incremental-jdt".equals(compilerId)) {
      new IncrementalCompiler(this, context.get()).compile();
    } else {
      throw new MojoExecutionException("Unsupported compilerId " + compilerId);
    }
  }

  @Override
  public final String getSource() {
    return source;
  }

  @Override
  public final String getTarget() {
    return target;
  }

  @Override
  public final File getPom() {
    return pom;
  }
}
