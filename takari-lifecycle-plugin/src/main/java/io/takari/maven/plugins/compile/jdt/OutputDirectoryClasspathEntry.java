/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathDirectory;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

class OutputDirectoryClasspathEntry implements ClasspathEntry, MutableClasspathEntry {

    // TODO convert to nio Path. for consistency

    private final File directory;

    /**
     * <strong>Live</strong> collection of output files to ignore. New files are added to the collection during lifespan of this OutputDirectoryClasspathEntry instance. The idea is to hide to-be-deleted
     * files from classpath.
     */
    private final Collection<File> staleOutputs;

    private ClasspathDirectory delegate;

    /**
     * @param staleOutputs is a <strong>live</strong> collection of output files to ignore.
     */
    public OutputDirectoryClasspathEntry(File directory, Collection<File> staleOutputs) {
        this.directory = directory;
        this.staleOutputs = staleOutputs;

        this.delegate = ClasspathDirectory.create(directory.toPath());
    }

    @Override
    public Collection<String> getPackageNames() {
        return delegate.getPackageNames();
    }

    @Override
    public NameEnvironmentAnswer findType(String packageName, String typeName) {
        Path file = delegate.getFile(packageName, typeName);
        if (file != null && !staleOutputs.contains(file.toFile())) {
            return delegate.findType(packageName, typeName, null);
        }
        return null;
    }

    @Override
    public void reset() {
        this.delegate = ClasspathDirectory.create(directory.toPath());
    }

    @Override
    public String toString() {
        return "Classpath for output directory " + directory;
    }

    @Override
    public String getEntryDescription() {
        return directory.getAbsolutePath();
    }
}
