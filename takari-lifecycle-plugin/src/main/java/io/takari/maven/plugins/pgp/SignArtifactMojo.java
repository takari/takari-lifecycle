package io.takari.maven.plugins.pgp;

import io.takari.jpgp.PgpArtifactSigner;
import io.takari.maven.plugins.TakariLifecycleMojo;
import java.io.File;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mojo(name = "signArtifact", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
public class SignArtifactMojo extends TakariLifecycleMojo {

  private final Logger logger = LoggerFactory.getLogger(SignArtifactMojo.class);

  @Parameter(property = "gpg.skip", defaultValue = "false")
  private boolean skip;

  @Parameter(property = "gpg.passphrase")
  private String passphrase;

  @Override
  protected void executeMojo() throws MojoExecutionException {
    File artifact = project.getArtifact().getFile();
    if(artifact == null) {
      logger.info("There is no artifact present. Make sure you run this after the package phase.");
      return;
    }
    try {
      PgpArtifactSigner signer = new PgpArtifactSigner();
      signer.sign(artifact);
    } catch (Exception e) {
      throw new MojoExecutionException("Error signing artifact " + artifact + ".", e);
    }
  }
}
