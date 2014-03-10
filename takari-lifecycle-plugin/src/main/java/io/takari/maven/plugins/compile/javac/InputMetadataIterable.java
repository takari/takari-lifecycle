package io.takari.maven.plugins.compile.javac;

import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.util.Iterator;

class InputMetadataIterable<T> implements Iterable<T> {

  private final Iterable<? extends InputMetadata<T>> iterable;

  private boolean unmodified = true;

  private class InputMetadataIterator implements Iterator<T> {

    private final Iterator<? extends InputMetadata<T>> iterator;

    public InputMetadataIterator(Iterator<? extends InputMetadata<T>> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override
    public T next() {
      InputMetadata<T> next = iterator.next();
      unmodified = unmodified && next.getStatus() == ResourceStatus.UNMODIFIED;
      return next.getResource();
    }

    @Override
    public void remove() {
      iterator.remove();
    }
  }

  public InputMetadataIterable(Iterable<? extends InputMetadata<T>> iterable) {
    this.iterable = iterable;
  }

  @Override
  public Iterator<T> iterator() {
    return new InputMetadataIterator(iterable.iterator());
  }

  public boolean isUnmodified() {
    return unmodified;
  }
}
