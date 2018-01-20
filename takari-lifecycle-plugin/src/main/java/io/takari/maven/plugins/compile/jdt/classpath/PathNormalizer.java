package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public class PathNormalizer {
  public static Path getCanonicalPath(Path file) {
    try {
      return file.toRealPath();
    } catch (NoSuchFileException e) {
      // Path#toRealPath() only works for existing files
      // return file.toFile().getCanonicalFile().toPath();
      return file.toAbsolutePath().normalize();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
