/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import static org.eclipse.jdt.internal.compiler.util.SuffixConstants.SUFFIX_STRING_class;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public class ClasspathDirectory extends AbstractClasspathDirectory implements ClasspathEntry {

    private ClasspathDirectory(Path directory, Set<String> packages, Map<String, Path> files) {
        super(directory, packages, files);
    }

    @Override
    public NameEnvironmentAnswer findType(String packageName, String typeName, AccessRestriction accessRestriction) {
        try {
            Path classFile = getFile(packageName, typeName);
            if (classFile != null) {
                try (InputStream is = Files.newInputStream(classFile)) {
                    return new NameEnvironmentAnswer(
                            ClassFileReader.read(is, classFile.getFileName().toString()), accessRestriction);
                }
            }
        } catch (ClassFormatException | IOException e) {
            // treat as if type is missing
        }
        return null;
    }

    @Override
    public String toString() {
        return "Classpath for directory " + file;
    }

    public static ClasspathDirectory create(Path directory) {
        Set<String> packages = new HashSet<>();
        Map<String, Path> files = new HashMap<>();
        scanDirectory(directory, SUFFIX_STRING_class, packages, files);
        return new ClasspathDirectory(directory, packages, files);
    }
}
