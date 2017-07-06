package io.takari.maven.plugins.compile.jdt;

import static io.takari.maven.testing.TestResources.create;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.processing.FilerException;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.apt.util.EclipseFileManager;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.core.builder.ProblemFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

import io.takari.maven.plugins.compile.CompilerBuildContext;
import io.takari.maven.plugins.compile.jdt.classpath.Classpath;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.ClasspathJar;
import io.takari.maven.plugins.compile.jdt.classpath.JavaInstallation;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;

public class FilerImplTest {

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

    try {
      create(outputDir, "test/resource.txt");
      filer.getResource(StandardLocation.SOURCE_OUTPUT, "test", "resource.txt");
      Assert.fail();
    } catch (FilerException expected) {
      // From Filer javadoc:
      // @throws FilerException if the same pathname has already been
      // opened for writing
    }
  }

  @Test
  public void testBinaryOriginatingElements() throws Exception {
    // the point of this test is to assert Filer#createSourceFile does not puke when originatingElements are not sources

    // originating source elements are used to cleanup generated outputs when corresponding sources change
    // originating binary elements are not currently fully supported and are not tracked during incremental build

    Classpath namingEnvironment = createClasspath();
    IErrorHandlingPolicy errorHandlingPolicy = DefaultErrorHandlingPolicies.exitAfterAllProblems();
    IProblemFactory problemFactory = ProblemFactory.getProblemFactory(Locale.getDefault());
    CompilerOptions compilerOptions = new CompilerOptions();
    ICompilerRequestor requestor = null;
    Compiler compiler = new Compiler(namingEnvironment, errorHandlingPolicy, compilerOptions, requestor, problemFactory);

    EclipseFileManager fileManager = new EclipseFileManager(null, Charsets.UTF_8);
    File outputDir = temp.newFolder();
    fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, Collections.singleton(outputDir));

    CompilerBuildContext context = null;
    Map<String, String> processorOptions = null;
    CompilerJdt incrementalCompiler = null;
    ProcessingEnvImpl env = new ProcessingEnvImpl(context, fileManager, processorOptions, compiler, incrementalCompiler);

    TypeElement typeElement = env.getElementUtils().getTypeElement("java.lang.Object");

    FilerImpl filer = new FilerImpl(null /* context */, fileManager, null /* compiler */, null /* env */);
    filer.createSourceFile("test.Source", typeElement);
  }

  @Test
  public void testResourceDoesNotExist() throws Exception {
    EclipseFileManager fileManager = new EclipseFileManager(null, Charsets.UTF_8);

    File outputDir = temp.newFolder();
    fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, Collections.singleton(outputDir));

    FilerImpl filer = new FilerImpl(null /* context */, fileManager, null /* compiler */, null /* env */);

    try {
      filer.getResource(StandardLocation.SOURCE_OUTPUT, "", "does-not-exist");
      Assert.fail();
    } catch (IOException expected) {
      // from Filer javadoc: @throws IOException if the file cannot be opened
    }
  }

  private Classpath createClasspath() throws IOException {
    final List<ClasspathEntry> entries = new ArrayList<ClasspathEntry>();
    final List<MutableClasspathEntry> mutableentries = new ArrayList<MutableClasspathEntry>();
    for (File file : JavaInstallation.getDefault().getClasspath()) {
      if (file.isFile()) {
        try {
          entries.add(ClasspathJar.create(file));
        } catch (IOException e) {
          // ignore, not a valid zip/jar
        }
      }
    }
    return new Classpath(entries, mutableentries);
  }

}
