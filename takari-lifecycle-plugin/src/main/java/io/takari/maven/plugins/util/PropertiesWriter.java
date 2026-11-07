/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Helper to strip idiotic timestamp comment from properties files
 */
public class PropertiesWriter {
    // properties files are documented to use ISO_8859_1 encoding
    private static final Charset ENCODING = StandardCharsets.ISO_8859_1;

    /**
     * Writes out provided properties, with or without comment (nullable), to provided output stream. The output stream
     * is not closed.
     */
    public static void write(Properties properties, String comment, OutputStream out) throws IOException {
        // store(OutputStream) writes non-Latin-1 chars as unicode escapes, store(Writer) does not
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        properties.store(buf, comment);
        List<String> lines =
                new ArrayList<>(Arrays.asList(buf.toString(ENCODING).split("\\R")));
        lines.remove(comment != null ? 1 : 0);
        lines.sort(String::compareTo); // make lines ordering stable
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n'); // not platform line separator: output must be OS-independent
        }
        out.write(sb.toString().getBytes(ENCODING));
    }
}
