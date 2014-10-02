/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.jar;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import io.tesla.proviso.archive.Entry;
import io.tesla.proviso.archive.Source;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Predicate;



/**
 * Archive source that wraps provided archive entries.
 * <p>
 * If multiple entries with the same name are present, only the first entry is included, other are silently ignored.
 */
class AggregateSource implements Source {

  private final List<Iterable<Entry>> sources;

  public AggregateSource(List<Iterable<Entry>> sources) {
    this.sources = sources;
  }

  @Override
  public Iterable<Entry> entries() {
    final Predicate<Entry> uniquePathFilter = new Predicate<Entry>() {
      private final Set<String> entryNames = new HashSet<>();

      @Override
      public boolean apply(Entry input) {
        return entryNames.add(input.getName());
      }
    };
    return filter(concat(sources), uniquePathFilter);
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public void close() throws IOException {}
}
