/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.eclipse.jdt.internal.compiler.util.Util;

public class JavaInstallation {
    private static final Predicate<Path> POTENTIAL_ZIP_FILTER =
            p -> Util.isPotentialZipArchive(p.getFileName().toString());

    private final List<Path> classpath;

    private JavaInstallation(List<Path> classpath) {
        this.classpath = Collections.unmodifiableList(new ArrayList<>(classpath));
    }

    /**
     * Returns default classpath associated with this java installation. The classpath includes bootstrap, extendion and endorsed entries.
     */
    public List<Path> getClasspath() {
        return classpath;
    }

    private static List<Path> getJava8() throws IOException {
        // mostly copy&paste from tycho
        // See org.eclipse.jdt.internal.compiler.batch.Main.setPaths

        List<Path> classpath = new ArrayList<>();

        Path javaHome = Util.getJavaHome().toPath();

        // boot classpath
        scanForArchives(classpath, javaHome.resolve("lib"));

        // endorsed libraries
        scanForArchives(classpath, javaHome.resolve("lib/endorsed"));

        // extension libraries
        scanForArchives(classpath, javaHome.resolve("lib/ext"));

        return classpath;
    }

    private static void scanForArchives(List<Path> classPathList, Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            Files.list(dir).filter(POTENTIAL_ZIP_FILTER).forEach(classPathList::add);
        }
    }

    private static List<Path> getJrtFs() throws IOException {
        // http://openjdk.java.net/jeps/220
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        List<Path> classpath = new ArrayList<>();
        for (Path root : fs.getRootDirectories()) {
            Files.list(root.resolve("modules")).forEach(classpath::add);
        }
        // technically, this leaks open FileSystem instance
        // which is okay, since singleton #instance is never released
        return classpath;
    }

    private static JavaInstallation instance;

    public static synchronized JavaInstallation getDefault() throws IOException {
        if (instance == null) {
            List<Path> cp;
            try {
                cp = getJrtFs();
            } catch (ProviderNotFoundException e) {
                cp = getJava8();
            }
            instance = new JavaInstallation(cp);
        }
        return instance;
    }
}
