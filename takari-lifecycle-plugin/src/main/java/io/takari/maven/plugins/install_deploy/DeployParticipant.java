package io.takari.maven.plugins.install_deploy;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.Maven;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@Singleton
public class DeployParticipant extends AbstractMavenLifecycleParticipant {

  private static final Logger log = LoggerFactory.getLogger(AbstractMavenLifecycleParticipant.class);

  protected RepositorySystem repoSystem;
  private List<DeployRequest> deployAtEndRequests = Collections.synchronizedList(new ArrayList<DeployRequest>());

  @Inject
  public DeployParticipant(RepositorySystem repoSystem) {
    this.repoSystem = repoSystem;
  }

  @Override
  public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
    boolean errors = !session.getResult().getExceptions().isEmpty();

    if (!deployAtEndRequests.isEmpty()) {

      log.info("");
      log.info("------------------------------------------------------------------------");

      if (errors) {
        log.info("-- Not performing deploy at end due to errors                         --");
      } else {
        log.info("-- Performing deploy at end                                           --");
        log.info("------------------------------------------------------------------------");

        synchronized (deployAtEndRequests) {
          for (DeployRequest deployRequest : deployAtEndRequests) {
            try {
              deploy(session.getRepositorySession(), deployRequest);
            } catch (DeploymentException e) {
              log.error(e.getMessage(), e);
              throw new MavenExecutionException(e.getMessage(), e);
            }
          }
          deployAtEndRequests.clear();
        }
      }

      log.info("------------------------------------------------------------------------");
    }
  }

  List<DeployRequest> getDeployAtEndRequests() {
    return Collections.unmodifiableList(deployAtEndRequests);
  }

  public void deploy(RepositorySystemSession session, DeployRequest deployRequest) throws DeploymentException {
    repoSystem.deploy(session, deployRequest);
  }

  public void deployAtEnd(DeployRequest deployRequest) {
    checkSupport();
    deployAtEndRequests.add(deployRequest);
  }

  private void checkSupport() {
    Properties properties = new Properties();

    try (InputStream in = Maven.class.getResourceAsStream("/META-INF/maven/org.apache.maven/maven-core/pom.properties")) {
      if (in != null) {
        properties.load(in);
      }
    } catch (IOException e) {
      log.error("Unable determine maven version, deploy at end might fail", e);
      return;
    }

    String mavenVersion = properties.getProperty("version");
    if (mavenVersion != null) {
      int c = new DefaultArtifactVersion(mavenVersion).compareTo(new DefaultArtifactVersion("3.3.1"));
      if (c < 0) {
        throw new IllegalStateException("Deploy-at-end is not supported on maven versions <3.3.1");
      }
    } else {
      log.error("Unable determine maven version, deploy at end might fail");
    }
  }

}
