/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.Test;

public class PropertiesWriterTest {
    @Test
    public void testNonLatin1AndLineSeparator() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("a", "ő中é");
        properties.setProperty("b", "plain");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PropertiesWriter.write(properties, "comment", out);

        String written = out.toString(StandardCharsets.ISO_8859_1);
        assertFalse(written.contains("\r"));
        assertEquals("#comment\na=\\u0151\\u4E2D\\u00E9\nb=plain\n", written);

        Properties read = new Properties();
        read.load(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(properties, read);
    }
}
