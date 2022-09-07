package io.takari.maven.plugins.pgp;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;

import io.takari.jpgp.ImmutablePgpSigningRequest;
import io.takari.jpgp.PgpSigner;
import io.takari.maven.plugins.TakariLifecycleMojo;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mojo(name = "signArtifact", configurator = "takari", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class SignArtifactMojo extends TakariLifecycleMojo {

  public static final String PGP_SIGNATURE_EXTENSION = ".asc";
  private final Logger logger = LoggerFactory.getLogger(SignArtifactMojo.class);

  @Parameter(property = "gpg.skip", defaultValue = "false")
  private boolean skip;

  @Parameter(property = "gpg.passphrase")
  private String passphrase;

  @Override
  protected void executeMojo() throws MojoExecutionException {

    if (skip) {
      logger.info("Skipping PGP signature generation as per configuration.");
      return;
    }

    List<SignedFile> mavenFilesToSign = new ArrayList<>();
    if (!"pom".equals(project.getPackaging())) {
      //
      // Primary artifact
      //
      org.apache.maven.artifact.Artifact artifact = project.getArtifact();
      File file = artifact.getFile();
      if (file == null) {
        logger.info("There is no artifact present. Make sure you run this after the package phase.");
        return;
      }
      mavenFilesToSign.add(new SignedFile(file.toPath(), artifact.getArtifactHandler().getExtension()));
    }

    //
    // POM
    //
    File pomToSign = new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".pom");
    try {
      createDirectories(pomToSign.getParentFile().toPath());
      copy(project.getFile().toPath(), pomToSign.toPath(), StandardCopyOption.REPLACE_EXISTING);
      mavenFilesToSign.add(new SignedFile(pomToSign.toPath(), "pom"));
    } catch (IOException e) {
      throw new MojoExecutionException("Error copying POM for signing.", e);
    }

    //
    // Attached artifacts
    //
    for (org.apache.maven.artifact.Artifact a : project.getAttachedArtifacts()) {
      mavenFilesToSign.add(new SignedFile(a.getFile().toPath(), a.getArtifactHandler().getExtension(), a.getClassifier()));
    }

    logger.debug("Signing the following files with PGP:");
    mavenFilesToSign.forEach(s -> logger.debug(s.toString()));
    PgpSigner pgpArtifactSigner = new PgpSigner(ImmutablePgpSigningRequest.builder().build());
    for (SignedFile pgpSignedFile : mavenFilesToSign) {
      Path file = pgpSignedFile.file();
      try {
        File pgpSignature = pgpArtifactSigner.sign(file.toFile());
        projectHelper.attachArtifact(project, pgpSignedFile.extension() + PGP_SIGNATURE_EXTENSION, pgpSignedFile.classifier(), pgpSignature);
      } catch (Exception e) {
        throw new MojoExecutionException("Error signing artifact " + file + ".", e);
      }
    }
  }
}
