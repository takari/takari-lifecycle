package io.tesla.maven.plugins.compiler;

import io.tesla.maven.plugins.compiler.jdt.IncrementalCompiler;
import io.tesla.maven.plugins.compiler.plexus.PlexusJavacCompiler;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.tesla.incremental.BuildContext;
import org.sonatype.maven.plugin.Conf;

public abstract class AbstractCompileMojo extends AbstractMojo
    implements
      InternalCompilerConfiguration {
  @Inject
  private Provider<BuildContext> context;

  /**
   * The compiler id of the compiler to use, {@code javac} or {@code incremental-jdt}.
   */
  @Conf(property = "maven.compiler.compilerId", defaultValue = "incremental-jdt")
  private String compilerId;

  /**
   * The -source argument for the Java compiler.
   */
  @Conf(property = "maven.compiler.source", defaultValue = "1.5")
  protected String source;

  /**
   * The -target argument for the Java compiler.
   */
  @Conf(property = "maven.compiler.target", defaultValue = "1.5")
  protected String target;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if ("incremental-jdt".equals(compilerId)) {
      new IncrementalCompiler(this, context.get()).compile();
    } else if ("javac".equals(compilerId)) {
      new PlexusJavacCompiler(this).compile();
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
}
