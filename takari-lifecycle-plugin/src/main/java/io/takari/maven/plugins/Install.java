/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;

/**
 * @author Jason van Zyl
 */
@Mojo(name = "install", defaultPhase = LifecyclePhase.INSTALL, configurator = "takari", threadSafe = true)
public class Install extends TakariLifecycleMojo {

    @Override
    public void executeMojo() throws MojoExecutionException {
        InstallRequest installRequest = new InstallRequest();
        installRequest.setArtifacts(projectArtifacts());

        try {
            repositorySystem.install(repositorySystemSession, installRequest);
        } catch (InstallationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
