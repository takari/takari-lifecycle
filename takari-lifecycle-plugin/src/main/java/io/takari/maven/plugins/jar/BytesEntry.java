/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import ca.vanzyl.provisio.archive.ExtendedArchiveEntry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

class BytesEntry implements ExtendedArchiveEntry {
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

    @Override
    public void setFileMode(int i) {}

    @Override
    public void setSize(long l) {}

    @Override
    public void setTime(long l) {}

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public String getSymbolicLinkPath() {
        return null;
    }

    @Override
    public boolean isHardLink() {
        return false;
    }

    @Override
    public String getHardLinkPath() {
        return null;
    }

    @Override
    public Date getLastModifiedDate() {
        return null;
    }
}
