/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/**
 * Various utilities.
 *
 * @since 2.1.2
 */
public final class Utilities
{
  private static final int BUFFER_SIZE = 8192;

  private Utilities() {
  }

  /**
   * Counts the elements in iterable.
   */
  public static int size(final Iterable<?> iterable) {
    if (iterable instanceof Collection ) {
      return ( (Collection<?>) iterable ).size();
    }
    int size = 0;
    for (Object o : iterable) {
      size++;
    }
    return size;
  }

  /**
   * Copies from input stream to output stream. None of passed in streams are closed.
   */
  public static void copy(final InputStream from, final OutputStream to) throws IOException {
    byte[] buf = new byte[BUFFER_SIZE];
    while (true) {
      int r = from.read(buf);
      if (r == -1) {
        break;
      }
      to.write(buf, 0, r);
    }
  }

  /**
   * Consumes full input stream and produces SHA-1 hash as byte array. The passed in stream is not closed.
   */
  public static byte[] sha1bytes(final InputStream inputStream) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        md.update(buffer, 0, read);
      }
      return md.digest();
    } catch ( NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unsupported JVM: sha1 MessageDigest algorithm unsupported", e);
    }
  }
}
