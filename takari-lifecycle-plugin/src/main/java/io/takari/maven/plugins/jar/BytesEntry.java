/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.tesla.proviso.archive.Entry;

class BytesEntry implements Entry {
  private final String entryName;
  private final byte[] contents;

  public BytesEntry(String entryName, byte[] contents) {
    this.entryName = entryName;
    this.contents = contents;
  }

  @Override
  public String getName() {
    return entryName;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(contents);
  }

  @Override
  public long getSize() {
    return contents.length;
  }

  @Override
  public void writeEntry(OutputStream outputStream) throws IOException {
    outputStream.write(contents);
  }

  @Override
  public int getFileMode() {
    return -1;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public boolean isExecutable() {
    return false;
  }

  @Override
  public long getTime() {
    return -1;
  }
}
