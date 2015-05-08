package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.apt.util.EclipseFileManager;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

public class FileImplTest {

  @Test
  public void testGetResource_unsupportedLocation() throws Exception {
    EclipseFileManager fileManager = new EclipseFileManager(null, Charsets.UTF_8);

    FilerImpl filer = new FilerImpl(null /* context */, fileManager, null /* compiler */, null /* env */);

    try {
      filer.getResource(StandardLocation.SOURCE_PATH, "", "test");
      Assert.fail();
    } catch (IllegalArgumentException expected) {
      // TODO check exception message
    }
  }

  @Test
  public void testGetResource_location_classpath() throws Exception {
    EclipseFileManager fileManager = new EclipseFileManager(null, Charsets.UTF_8);

    List<File> classpath = new ArrayList<>();
    classpath.add(new File("src/test/projects/compile-jdt-proc/getresource-location-classpath/classes"));
    classpath.add(new File("src/test/projects/compile-jdt-proc/getresource-location-classpath/dependency.zip"));
    fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);

    FilerImpl filer = new FilerImpl(null /* context */, fileManager, null /* compiler */, null /* env */);

    Assert.assertEquals("dir resource", toString(filer.getResource(StandardLocation.CLASS_PATH, "", "dirresource.txt")));
    // Assert.assertEquals("jar resource", toString(filer.getResource(StandardLocation.CLASS_PATH, "", "jarresource.txt")));
    Assert.assertEquals("pkg jar resource", toString(filer.getResource(StandardLocation.CLASS_PATH, "pkg", "jarresource.txt")));
  }

  private static String toString(FileObject file) throws IOException {
    try (InputStream is = file.openInputStream()) {
      return CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
    }
  }
}
