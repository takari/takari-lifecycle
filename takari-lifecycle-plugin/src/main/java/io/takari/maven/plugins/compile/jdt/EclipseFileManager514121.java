package io.takari.maven.plugins.compile.jdt;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;

import org.eclipse.jdt.internal.compiler.apt.util.EclipseFileManager;

/**
 * Workaround open URLClassLoader leak in {@link EclipseFileManager}
 * 
 * @see https://bugs.eclipse.org/bugs/show_bug.cgi?id=514121
 */
class EclipseFileManager514121 extends EclipseFileManager {

  private final HashMap<Location, ClassLoader> classloaders = new HashMap<>();

  public EclipseFileManager514121(Locale locale, Charset charset) {
    super(locale, charset);
  }

  @Override
  public ClassLoader getClassLoader(Location location) {
    synchronized (classloaders) {
      ClassLoader cl = classloaders.get(location);
      if (cl == null) {
        cl = super.getClassLoader(location);
        classloaders.put(location, cl);
      }
      return cl;
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (classloaders) {
      for (ClassLoader cl : classloaders.values()) {
        if (cl instanceof Closeable) {
          ((Closeable) cl).close();
        }
      }
    }
    super.close();
  }
}
