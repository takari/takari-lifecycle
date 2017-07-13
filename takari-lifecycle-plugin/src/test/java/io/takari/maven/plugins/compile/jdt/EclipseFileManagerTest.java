package io.takari.maven.plugins.compile.jdt;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.apt.util.EclipseFileManager;
import org.junit.Test;

public class EclipseFileManagerTest {

  @Test
  public void test514121_closeClassloaders() throws Exception {
    EclipseFileManager fileManager = new EclipseFileManager(null, StandardCharsets.UTF_8);
    List<File> classpath = new ArrayList<>();
    classpath.add(new File("src/test/jars/commons-lang-2.0.jar"));
    fileManager.setLocation(StandardLocation.ANNOTATION_PROCESSOR_PATH, classpath);
    URLClassLoader loader = (URLClassLoader) fileManager.getClassLoader(StandardLocation.ANNOTATION_PROCESSOR_PATH);
    assertNotNull(loader.findResource("META-INF/LICENSE.txt")); // sanity check
    fileManager.close();
    assertNull(loader.findResource("META-INF/LICENSE.txt")); // assert the classloader is closed
  }
}
