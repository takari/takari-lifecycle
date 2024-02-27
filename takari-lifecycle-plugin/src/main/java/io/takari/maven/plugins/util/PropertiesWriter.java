/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
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
        StringWriter sw = new StringWriter();
        properties.store(sw, comment);
        List<String> lines = new ArrayList<>(Arrays.asList(sw.toString().split("\\R")));
        lines.remove(comment != null ? 1 : 0);
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, ENCODING));
        for (String line : lines) {
            w.write(line);
            w.newLine();
        }
        w.flush();
    }
}
