/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import static org.eclipse.jdt.internal.compiler.util.SuffixConstants.SUFFIX_STRING_java;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public class SourcepathDirectory extends AbstractClasspathDirectory {

    public static class ClasspathCompilationUnit extends CompilationUnit {
        public ClasspathCompilationUnit(Path file, String encoding) {
            super(
                    null /* contents */,
                    file.toAbsolutePath().toString(),
                    encoding,
                    null /* destinationPath */,
                    false /* ignoreOptionalProblems */,
                    null /* modName */);
        }
    }

    private final String encoding;

    private SourcepathDirectory(Path directory, Set<String> packages, Map<String, Path> files, Charset encoding) {
        super(directory, packages, files);
        this.encoding = encoding != null ? encoding.name() : null;
    }

    @Override
    public NameEnvironmentAnswer findType(String packageName, String typeName, AccessRestriction accessRestriction) {
        Path javaFile = getFile(packageName, typeName);

        // Could be looking for a nested class, so try using outer class file name.
        // ASSUMPTION: '$' is ONLY used in a compiler generated class file names.
        if (javaFile == null && typeName.indexOf("$") > 0) {
            javaFile = getFile(packageName, typeName.split("\\$")[0]);
        }

        if (javaFile != null) {
            CompilationUnit cu = new ClasspathCompilationUnit(javaFile, encoding);
            return new NameEnvironmentAnswer(cu, accessRestriction);
        }
        return null;
    }

    @Override
    public String toString() {
        return "Sourcepath for directory " + file;
    }

    public static SourcepathDirectory create(Path directory, Charset encoding) {
        Set<String> packages = new HashSet<>();
        Map<String, Path> files = new HashMap<>();
        scanDirectory(directory, SUFFIX_STRING_java, packages, files);
        return new SourcepathDirectory(directory, packages, files, encoding);
    }
}
