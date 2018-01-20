package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.junit.Assert;
import org.junit.Test;

public class ClasspathTest {

  @Test
  public void testEmptyJarPackage() throws Exception {
    final List<ClasspathEntry> entries = new ArrayList<ClasspathEntry>();
    for (Path file : JavaInstallation.getDefault().getClasspath()) {
      if (Files.isRegularFile(file)) {
        try {
          entries.add(ClasspathJar.create(file));
        } catch (IOException e) {
          // ignore
        }
      } else if (Files.isDirectory(file)) {
        entries.add(ClasspathDirectory.create(file));
      }
    }
    Classpath classpath = new Classpath(entries, null);
    Assert.assertTrue(classpath.isPackage(CharOperation.splitOn('.', "org".toCharArray()), "xml".toCharArray()));
  }

  @Test
  public void testCaseInsensitive() throws IOException {
    // affects windows and osx, linux users should not apply
    File sourceRoot = new File("target/test-classes").getCanonicalFile();
    ClasspathEntry cpe = ClasspathDirectory.create(sourceRoot.toPath());
    String pkg = getClass().getPackage().getName().replace('.', '/');
    String cls = getClass().getSimpleName();
    Assert.assertNotNull(cpe.findType(pkg, cls));
    Assert.assertNull(cpe.findType(pkg, cls.toLowerCase()));
    Assert.assertNull(cpe.findType(pkg.toUpperCase(), cls));
  }
}
