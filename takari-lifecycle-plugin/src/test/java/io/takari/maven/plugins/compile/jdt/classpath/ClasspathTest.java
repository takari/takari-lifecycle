package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.junit.Assert;
import org.junit.Test;

public class ClasspathTest {

  @Test
  public void testEmptyJarPackage() throws Exception {
    final List<ClasspathEntry> entries = new ArrayList<ClasspathEntry>();
    entries.addAll(JavaInstallation.getDefault().getClasspath());
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
