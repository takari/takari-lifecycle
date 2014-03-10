package io.takari.maven.plugins.compile.javac;

import java.io.File;

import javax.tools.FileObject;

class FileObjects {
  public static File toFile(FileObject fileObject) {
    // java6 returns non-absolute URI that cannot be used with new File(URI)
    return new File(fileObject.toUri().getPath());
  }
}
