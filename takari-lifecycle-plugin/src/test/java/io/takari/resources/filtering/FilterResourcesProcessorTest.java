/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.resources.filtering;

import static io.takari.resources.filtering.FilterResourcesProcessor.escapeWindowsPath;
import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.Properties;
import org.junit.Test;

public class FilterResourcesProcessorTest {

    @Test
    public void testEscapeWindowsPath() throws Exception {
        assertEquals("C:\\\\Users\\\\runner\\\\.m2", escapeWindowsPath("C:\\Users\\runner\\.m2"));
        // already escaped stays as is
        assertEquals("C:\\\\Users\\\\runner", escapeWindowsPath("C:\\\\Users\\\\runner"));
        // not a windows path: untouched
        assertEquals("a\\b", escapeWindowsPath("a\\b"));
        assertEquals("/home/user/.m2", escapeWindowsPath("/home/user/.m2"));

        // round-trips through a .properties file
        Properties p = new Properties();
        p.load(new StringReader("v=" + escapeWindowsPath("C:\\Users\\runner\\.m2\\repository")));
        assertEquals("C:\\Users\\runner\\.m2\\repository", p.getProperty("v"));
    }
}
