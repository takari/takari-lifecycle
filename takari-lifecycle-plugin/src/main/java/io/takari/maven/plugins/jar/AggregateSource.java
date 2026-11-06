/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import ca.vanzyl.provisio.archive.Source;
import ca.vanzyl.provisio.archive.SourceEntry;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Archive source that wraps provided archive entries.
 * <p>
 * If multiple entries with the same name are present, only the first entry is included, other are silently ignored.
 */
class AggregateSource implements Source {

    private final List<Iterable<SourceEntry>> sources;

    public AggregateSource(List<Iterable<SourceEntry>> sources) {
        this.sources = sources;
    }

    @Override
    public void forEachEntry(EntryConsumer consumer) throws IOException {
        Set<String> entryNames = new HashSet<>();
        for (Iterable<SourceEntry> source : sources) {
            for (SourceEntry entry : source) {
                if (entryNames.add(entry.getName())) {
                    consumer.accept(entry);
                }
            }
        }
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public void close() throws IOException {}
}
