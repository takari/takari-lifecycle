package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.junit.Assert;
import org.junit.Test;

public class ClasspathTest {

  @Test
  public void testEmptyJarPackage() throws Exception {
    final List<ClasspathEntry> entries = new ArrayList<ClasspathEntry>();
    for (File file : JavaInstallation.getDefault().getClasspath()) {
      if (file.isFile()) {
        try {
          entries.add(new ClasspathJar(file));
        } catch (IOException e) {
          // ignore
        }
      } else if (file.isDirectory()) {
        entries.add(new ClasspathDirectory(file));
      }
    }
    Classpath classpath = new Classpath(entries, null);
    Assert.assertTrue(classpath.isPackage(CharOperation.splitOn('.', "org".toCharArray()),
        "xml".toCharArray()));
  }

  @Test
  public void testCaseInsensitiveLookup() {
    // affects windows and osx, linux users should not apply
    File sourceRoot = new File("src/test/projects/compile/basic/src/main/java");
    ClasspathEntry cpe = new ClasspathDirectory(sourceRoot, true, null);
    Assert.assertNull(cpe.findType("basic", "basic.class"));
  }
}
