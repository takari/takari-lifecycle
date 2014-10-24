/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.javac;

/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies
 * this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

// duplicates io.takari.incrementalbuild.spi.IncrementalFileOutputStream
// this is necessary to provide IncrementalFileOutputStream for forked compiler executions
// ideally, this class should be moved to a helper library also used by incrementalbuild,
// but is not currently supported by mojo unit test harness, and likely will never be
// (in order to add helper library to classpath of forked compiler execution during unit tests, 
// the test harness will need to resolve the plugin dependency artifacts somehow, something that
// can only be done in the "outer" build and then passed into the test. doable, but a lot of work)
class IncrementalFileOutputStream extends OutputStream {

  public static final int BUF_SIZE = 1024 * 16;

  private final RandomAccessFile raf;

  private final byte[] buffer;

  private boolean modified;

  public IncrementalFileOutputStream(File file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("output file not specified");
    }

    File parent = file.getParentFile();

    if (!parent.isDirectory() && !parent.mkdirs()) {
      throw new IOException("Could not create directory " + parent);
    }

    modified = !file.exists();

    if (file.exists() && !file.canWrite()) {
      file.setWritable(true);
    }

    raf = new RandomAccessFile(file, "rw");
    buffer = new byte[BUF_SIZE];
  }

  @Override
  public void close() throws IOException {
    long pos = raf.getFilePointer();
    if (pos < raf.length()) {
      modified = true;
      raf.setLength(pos);
    }
    raf.close();
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    if (modified) {
      raf.write(b, off, len);
    } else {
      for (int n = len; n > 0;) {
        int read = raf.read(buffer, 0, Math.min(buffer.length, n));
        if (read < 0 || !arrayEquals(b, off + len - n, buffer, 0, read)) {
          modified = true;
          if (read > 0) {
            raf.seek(raf.getFilePointer() - read);
          }
          raf.write(b, off + len - n, n);
          break;
        } else {
          n -= read;
        }
      }
    }
  }

  private boolean arrayEquals(byte[] a1, int off1, byte[] a2, int off2, int len) {
    for (int i = 0; i < len; i++) {
      if (a1[off1 + i] != a2[off2 + i]) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void write(int b) throws IOException {
    if (modified) {
      raf.write(b);
    } else {
      int i = raf.read();
      if (i < 0 || i != (b & 0xFF)) {
        modified = true;
        if (i >= 0) {
          raf.seek(raf.getFilePointer() - 1);
        }
        raf.write(b);
      }
    }
  }

}
