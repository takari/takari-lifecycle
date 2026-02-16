/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.packaging;

import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;

@Singleton
@Named("takari-maven-plugin")
public class TakariMavenPluginArtifactHandler extends DefaultArtifactHandler {
    public TakariMavenPluginArtifactHandler() {
        super("takari-maven-plugin");
        setExtension("jar");
        setLanguage("java");
        setAddedToClasspath(true);
    }
}
