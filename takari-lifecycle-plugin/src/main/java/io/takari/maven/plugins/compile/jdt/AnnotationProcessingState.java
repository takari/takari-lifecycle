/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * State necessary to implement all-or-nothing annotation processing behaviour.
 */
class AnnotationProcessingState implements Serializable {

    public final Set<File> processedSources;
    public final ReferenceCollection referencedTypes;
    public final Set<File> writtenOutputs;

    public AnnotationProcessingState(
            Set<File> processedSources, ReferenceCollection referencedTypes, Set<File> writtenOutputs) {
        this.processedSources = Collections.unmodifiableSet(new LinkedHashSet<>(processedSources));
        this.referencedTypes = referencedTypes;
        this.writtenOutputs = Collections.unmodifiableSet(new LinkedHashSet<>(writtenOutputs));
    }
}
