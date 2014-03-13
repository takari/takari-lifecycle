package io.takari.maven.plugins.compile.javac;

import java.io.*;

import javax.tools.*;

abstract class RecordingJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

  protected RecordingJavaFileManager(StandardJavaFileManager fileManager) {
    super(fileManager);
  }

  @Override
  public FileObject getFileForOutput(Location location, String packageName, String relativeName,
      FileObject sibling) throws IOException {
    FileObject fileObject = super.getFileForOutput(location, packageName, relativeName, sibling);
    record(FileObjects.toFile(fileObject));
    return new ForwardingFileObject<FileObject>(fileObject) {
      @Override
      public OutputStream openOutputStream() throws IOException {
        return new IncrementalFileOutputStream(FileObjects.toFile(this));
      }
    };
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String className,
      javax.tools.JavaFileObject.Kind kind, FileObject sibling) throws IOException {
    JavaFileObject fileObject = super.getJavaFileForOutput(location, className, kind, sibling);
    record(FileObjects.toFile(fileObject));
    return new ForwardingJavaFileObject<JavaFileObject>(fileObject) {
      @Override
      public OutputStream openOutputStream() throws IOException {
        return new IncrementalFileOutputStream(FileObjects.toFile(this));
      }
    };
  }

  // tooling API is rather vague about sibling. it "javac might provide
  // the originating source file as sibling" but this does not appear to be
  // guaranteed. even though sibling appear to be the source during the test,
  // the current implementation does not rely on this uncertain javac behaviour
  protected abstract void record(File outputFile);

  @Override
  public boolean isSameFile(FileObject a, FileObject b) {
    return a.toUri().equals(b.toUri());
  }
}
