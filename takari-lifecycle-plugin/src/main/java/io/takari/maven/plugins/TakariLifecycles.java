/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins;

public enum TakariLifecycles {
    TAKARI_JAR("takari-jar"),
    TAKARI_MAVEN_PLUGIN("takari-maven-plugin"),
    TAKARI_MAVEN_COMPONENT("takari-maven-component"),
    // while testing we're not setting any specific Takari lifecycle explicitly in our tests so many
    // of the JAR Mojo tests fail because we now explicitly want one of the Takari lifecycles to make
    // the JAR produced by the JAR Mojo the primary artifact.
    TAKARI_TESTING("jar");

    private String lifecycle;

    TakariLifecycles(String lifecycle) {
        this.lifecycle = lifecycle;
    }

    public static boolean isJarProducingTakariLifecycle(String lifecycle) {
        return TAKARI_JAR.lifecycle.equals(lifecycle)
                || TAKARI_MAVEN_PLUGIN.lifecycle.equals(lifecycle)
                || TAKARI_MAVEN_COMPONENT.lifecycle.equals(lifecycle)
                || TAKARI_TESTING.lifecycle.equals(lifecycle);
    }
}
