/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.install_deploy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@Singleton
public class DeployParticipant extends AbstractMavenLifecycleParticipant {

    private static final Logger log = LoggerFactory.getLogger(AbstractMavenLifecycleParticipant.class);

    protected RepositorySystem repoSystem;
    private final List<DeployRequest> deployAtEndRequests = Collections.synchronizedList(new ArrayList<>());

    // Maven calls lifecycle participants only from build extension realms; deploy-at-end requires it
    private volatile boolean active;

    @Inject
    public DeployParticipant(RepositorySystem repoSystem) {
        this.repoSystem = repoSystem;
    }

    @Override
    public void afterProjectsRead(MavenSession session) {
        active = true;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
        try {
            doAfterSessionEnd(session);
        } finally {
            // singleton outlives the session (e.g. mvnd): never leak state into the next build
            deployAtEndRequests.clear();
            active = false;
        }
    }

    private void doAfterSessionEnd(MavenSession session) throws MavenExecutionException {
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
                    HashMap<RemoteRepository, DeployRequest> batched = new HashMap<>();
                    for (DeployRequest deployRequest : deployAtEndRequests) {
                        if (!batched.containsKey(deployRequest.getRepository())) {
                            batched.put(deployRequest.getRepository(), deployRequest);
                        } else {
                            DeployRequest dr = batched.get(deployRequest.getRepository());
                            deployRequest.getArtifacts().forEach(dr::addArtifact);
                            deployRequest.getMetadata().forEach(dr::addMetadata);
                        }
                    }
                    try {
                        for (DeployRequest dr : batched.values()) {
                            deploy(session.getRepositorySession(), dr);
                        }
                    } catch (DeploymentException e) {
                        log.error(e.getMessage(), e);
                        throw new MavenExecutionException(e.getMessage(), e);
                    }
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
        deployAtEndRequests.add(deployRequest);
    }
}
