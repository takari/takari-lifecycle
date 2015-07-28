package io.takari.maven.plugins.exportpackage;

import static io.takari.maven.testing.TestResources.create;
import static io.takari.maven.testing.TestResources.rm;
import static io.takari.maven.testing.TestResources.touch;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.codehaus.plexus.util.Os;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

public class ExportPackageMojoTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("exportpackage/basic");

    // initial build
    mkfile(basedir, "target/classes/exported/Exported.class");
    mkfile(basedir, "target/classes/internal/Internal.class");
    mojos.executeMojo(basedir, "export-package");
    mojos.assertBuildOutputs(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
    assertExportedPackages(basedir, "exported");

    // no-change rebuild
    mojos.executeMojo(basedir, "export-package");
    mojos.assertCarriedOverOutputs(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
    assertExportedPackages(basedir, "exported");

    // change public and private classes
    touch(basedir, "target/classes/exported/Exported.class");
    touch(basedir, "target/classes/internal/Internal.class");
    mojos.executeMojo(basedir, "export-package");
    mojos.assertCarriedOverOutputs(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
    assertExportedPackages(basedir, "exported");

    // remove private class
    rm(basedir, "target/classes/internal/Internal.class");
    mojos.executeMojo(basedir, "export-package");
    mojos.assertCarriedOverOutputs(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
    assertExportedPackages(basedir, "exported");

    // new public class
    mkfile(basedir, "target/classes/exported/another/Exported.class");
    mojos.executeMojo(basedir, "export-package");
    mojos.assertBuildOutputs(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
    assertExportedPackages(basedir, "exported", "exported.another");

    // remove public class
    rm(basedir, "target/classes/exported/another/Exported.class");
    mojos.executeMojo(basedir, "export-package");
    mojos.assertBuildOutputs(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
    assertExportedPackages(basedir, "exported");

    // remove last public class
    rm(basedir, "target/classes/exported/Exported.class");
    mojos.executeMojo(basedir, "export-package");
    mojos.assertBuildOutputs(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
    assertExportedPackages(basedir, new String[0]);
  }

  @Test
  public void testNoClassesDirectory() throws Exception {
    File basedir = resources.getBasedir("exportpackage/basic");
    mojos.executeMojo(basedir, "export-package");
    mojos.assertBuildOutputs(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
    assertExportedPackages(basedir, new String[0]);
  }

  @Test
  public void testBasic_symlinked() throws Exception {
    Assume.assumeTrue(Os.isFamily(Os.FAMILY_UNIX));

    File basedir = resources.getBasedir();

    File orig = new File(basedir, "orig");
    create(orig, "target/classes/exported/Class.class");
    File symlink = java.nio.file.Files.createSymbolicLink(new File(basedir, "symlink").toPath(), orig.toPath()).toFile();

    mojos.executeMojo(symlink, "export-package");
    assertExportedPackages(symlink, "exported");
  }

  private void assertExportedPackages(File basedir, String... exportedPackages) throws IOException {
    List<String> actual = Files.readLines(new File(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE), Charsets.UTF_8);
    Assert.assertEquals(toString(Arrays.asList(exportedPackages)), toString(actual));
  }

  private String toString(Collection<String> strings) {
    StringBuilder sb = new StringBuilder();
    for (String string : new TreeSet<>(strings)) {
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(string);
    }
    return sb.toString();
  }

  private void mkfile(File basedir, String relpath) throws IOException {
    File file = new File(basedir, relpath);
    file.getParentFile().mkdirs();
    file.createNewFile();
  }
}
