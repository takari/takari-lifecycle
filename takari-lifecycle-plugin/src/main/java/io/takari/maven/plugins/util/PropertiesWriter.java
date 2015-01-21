/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.CharStreams;

/**
 * Helper to strip idiotic timestamp comment from properties files
 */
public class PropertiesWriter {
  // properties files are documented to use ISO_8859_1 encoding
  private static final Charset ENCODING = Charsets.ISO_8859_1;

  public static void write(Properties properties, String comment, OutputStream out) throws IOException {
    StringBuilder sb = new StringBuilder();
    properties.store(CharStreams.asWriter(sb), comment);
    write(CharSource.wrap(sb.toString()), comment, out);
  }

  public static void write(byte[] properties, OutputStream out) throws IOException {
    // properties files are documented to use ISO_8859_1 encoding
    write(ByteSource.wrap(properties).asCharSource(ENCODING), null, out);
  }

  private static void write(CharSource charSource, String comment, OutputStream out) throws IOException {
    List<String> lines = new ArrayList<>(charSource.readLines());
    lines.remove(comment != null ? 1 : 0);
    BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, ENCODING));
    for (String line : lines) {
      w.write(line);
      w.newLine();
    }
    w.flush();
  }
}
