package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.processing.FilerException;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.apt.util.EclipseFileManager;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

public class FileImplTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

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

  @Test
  public void testRecreateSourceFile() throws Exception {
    EclipseFileManager fileManager = new EclipseFileManager(null, Charsets.UTF_8);

    File outputDir = temp.newFolder();
    fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, Collections.singleton(outputDir));

    FilerImpl filer = new FilerImpl(null /* context */, fileManager, null /* compiler */, null /* env */);

    filer.createSourceFile("test.Source");
    try {
      filer.createSourceFile("test.Source");
      Assert.fail();
    } catch (FilerException expected) {
      // From Filer javadoc:
      // @throws FilerException if the same pathname has already been
      // created, the same type has already been created, or the name is
      // not valid for a type
    }
  }

  @Test
  public void testRecreateResource() throws Exception {
    EclipseFileManager fileManager = new EclipseFileManager(null, Charsets.UTF_8);

    File outputDir = temp.newFolder();
    fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, Collections.singleton(outputDir));

    FilerImpl filer = new FilerImpl(null /* context */, fileManager, null /* compiler */, null /* env */);

    filer.createResource(StandardLocation.SOURCE_OUTPUT, "test", "resource.txt");
    try {
      filer.createResource(StandardLocation.SOURCE_OUTPUT, "test", "resource.txt");
      Assert.fail();
    } catch (FilerException expected) {
      // From Filer javadoc:
      // @throws FilerException if the same pathname has already been
      // created
    }
  }

}
