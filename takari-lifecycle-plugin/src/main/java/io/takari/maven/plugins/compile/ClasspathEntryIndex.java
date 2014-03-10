package io.takari.maven.plugins.compile;

import com.google.common.collect.Multimap;

public class ClasspathEntryIndex {

  private final Multimap<String, byte[]> index;
  private final boolean persistent;

  ClasspathEntryIndex(Multimap<String, byte[]> index, boolean persistent) {
    this.index = index;
    this.persistent = persistent;
  }

  public Multimap<String, byte[]> getIndex() {
    return index;
  }

  public boolean isPersistent() {
    return persistent;
  }
}
