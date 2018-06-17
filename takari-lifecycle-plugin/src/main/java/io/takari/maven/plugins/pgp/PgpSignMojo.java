package io.takari.maven.plugins.pgp;

/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.artifact.SubArtifact;

import io.takari.jpgp.PGPMessageSigner;
import io.takari.jpgp.loaders.FilePassphraseLoader;
import io.takari.jpgp.loaders.GPGAgentPassphraseLoader;
import io.takari.jpgp.loaders.KeyRingLoader;
import io.takari.maven.plugins.TakariLifecycleMojo;
import io.takari.maven.plugins.util.AetherUtils;

/**
 * Mojo using a pure Java mechanism to PGP sign artifacts, generally used as part of the release process.
 * 
 * @author Jason van Zyl
 */
@Mojo(name = "sign", defaultPhase = LifecyclePhase.VERIFY, configurator = "takari")
public class PgpSignMojo extends TakariLifecycleMojo {

  private static final File DEFAULT_KEYRING = new File(new File(System.getProperty("user.home")), ".gnupg/secring.gpg");

  @Parameter(defaultValue = "${project.build.directory}")
  private File outputDirectory;

  @Parameter(property = "keyRing")
  protected File keyRing = DEFAULT_KEYRING;

  @Parameter(property = "passphraseFile")
  protected File passphraseFile;

  private final KeyRingLoader keyringLoader;
  private final GPGAgentPassphraseLoader gpgAgentPassphraseLoader;
  private final FilePassphraseLoader filePassphraseLoader;
  private final PGPMessageSigner signer;

  public PgpSignMojo() {
    keyringLoader = new KeyRingLoader();
    gpgAgentPassphraseLoader = new GPGAgentPassphraseLoader();
    filePassphraseLoader = new FilePassphraseLoader();
    signer = new PGPMessageSigner();
  }

  @Override
  public void executeMojo() throws MojoExecutionException {

    // Attached artifacts
    for (org.apache.maven.artifact.Artifact attachedArtifact : project.getAttachedArtifacts()) {
      Artifact artifact = AetherUtils.toArtifact(attachedArtifact);
      createSignatureAndAttachToProject(AetherUtils.toArtifact(attachedArtifact), artifact.getFile());
    }

    if ("pom".equals(project.getPackaging())) {
      // POM-project primary artifact
      Artifact pomArtifact = AetherUtils.toArtifact(project.getArtifact());
      pomArtifact = pomArtifact.setFile(project.getFile());
      createSignatureAndAttachToProject(pomArtifact, new File(outputDirectory, artifactFile(pomArtifact)));
    } else {
      // Primary artifact
      Artifact artifact = AetherUtils.toArtifact(project.getArtifact());
      // Seems there might be a bug in the takari test tools the primary artifact is coming out as the pom.xml
      // so I have to reconstruct it here to get the right file name for the primary artifact.
      File artifactFile = new File(outputDirectory, artifactFile(artifact));
      artifact = artifact.setFile(artifactFile);
      createSignatureAndAttachToProject(artifact, artifactFile);
      // POM
      Artifact pomArtifact = new SubArtifact(artifact, "", "pom");
      pomArtifact = pomArtifact.setFile(project.getFile());
      createSignatureAndAttachToProject(pomArtifact, new File(outputDirectory, artifactFile(pomArtifact)));
    }
  }

  public File createSignatureAndAttachToProject(Artifact a, File file) throws MojoExecutionException {
    File signatureFile = new File(file + ".asc");
    try {
      if (a.getFile() != file) {
        // Cover the case where we are copying something like a pom.xml to target/foo-1.0.pom
        FileUtils.copyFile(a.getFile(), file);
      }
      PGPSecretKey secretKey = keyringLoader.load(keyRing);
      try (InputStream message = new FileInputStream(file); OutputStream signature = new FileOutputStream(signatureFile)) {
        String passphrase;
        if (passphraseFile != null && passphraseFile.exists()) {
          passphrase = filePassphraseLoader.load(secretKey, passphraseFile.getAbsolutePath());
        } else {
          passphrase = gpgAgentPassphraseLoader.load(secretKey);
        }
        signer.signMessage(secretKey, passphrase, message, signature);
      }
      projectHelper.attachArtifact(project, a.getExtension() + ".asc", a.getClassifier(), signatureFile);
      return signatureFile;
    } catch (IOException e) {
      throw new MojoExecutionException(String.format("Error signing artifact %s.", file), e);
    }
  }

  public String artifactFile(Artifact a) {
    StringBuffer sb = new StringBuffer();
    sb.append(a.getArtifactId()).append("-").append(a.getVersion()).toString();
    if (!a.getClassifier().isEmpty()) {
      sb.append("-").append(a.getClassifier());
    }
    sb.append(".").append(a.getExtension());
    return sb.toString();
  }
}
