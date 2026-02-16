/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class PathNormalizer {
    public static Path getCanonicalPath(Path file) {
        try {
            return file.toRealPath();
        } catch (NoSuchFileException e) {
            // Path#toRealPath() only works for existing files
            // return file.toFile().getCanonicalFile().toPath();
            return file.toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
