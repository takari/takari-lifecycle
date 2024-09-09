/*
 * Copyright (c) 2014-2024 Takari, Inc.
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
@Named("takari-jar")
public class TakariJarArtifactHandler extends DefaultArtifactHandler {
    public TakariJarArtifactHandler() {
        super("takari-jar");
        setExtension("jar");
        setLanguage("java");
        setAddedToClasspath(true);
    }
}
