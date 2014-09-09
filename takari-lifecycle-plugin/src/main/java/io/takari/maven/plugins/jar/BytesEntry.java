package io.takari.maven.plugins.jar;

import io.tesla.proviso.archive.Entry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

}
