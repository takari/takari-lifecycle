package io.takari.maven.plugins.compile.javac;

import java.io.File;
import java.net.URI;

import javax.tools.FileObject;

class FileObjects {
  public static File toFile(FileObject fileObject) {
    // java6 returns non-absolute URI that cannot be used with new File(URI)
    // java7/8 produce messages for .class resources with jar:file:/ URIs

    URI uri = fileObject.toUri();
    if (uri == null) {
      return null;
    }
    String path = uri.getPath();
    if (path == null) {
      return null;
    }
    return new File(path);
  }
}
