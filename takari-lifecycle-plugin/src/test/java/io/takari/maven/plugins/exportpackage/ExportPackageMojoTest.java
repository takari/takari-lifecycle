package io.takari.maven.plugins.exportpackage;

import static io.takari.maven.testing.TestResources.create;
import static io.takari.maven.testing.TestResources.rm;
import static io.takari.maven.testing.TestResources.touch;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.codehaus.plexus.util.Os;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

public class ExportPackageMojoTest {

    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final IncrementalBuildRule mojos = new IncrementalBuildRule();

    @Test
    public void testBasic() throws Exception {
        Path basedir = resources.getBasedir("exportpackage/basic").toPath();

        // initial build
        mkfile(basedir, "target/classes/exported/Exported.class");
        mkfile(basedir, "target/classes/internal/Internal.class");
        mojos.executeMojo(basedir.toFile(), "export-package");
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
        assertExportedPackages(basedir, "exported");

        // no-change rebuild
        mojos.executeMojo(basedir.toFile(), "export-package");
        mojos.assertCarriedOverOutputs(basedir.toFile(), "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
        assertExportedPackages(basedir, "exported");

        // change public and private classes
        touch(basedir.toFile(), "target/classes/exported/Exported.class");
        touch(basedir.toFile(), "target/classes/internal/Internal.class");
        mojos.executeMojo(basedir.toFile(), "export-package");
        mojos.assertCarriedOverOutputs(basedir.toFile(), "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
        assertExportedPackages(basedir, "exported");

        // remove private class
        rm(basedir.toFile(), "target/classes/internal/Internal.class");
        mojos.executeMojo(basedir.toFile(), "export-package");
        mojos.assertCarriedOverOutputs(basedir.toFile(), "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
        assertExportedPackages(basedir, "exported");

        // new public class
        mkfile(basedir, "target/classes/exported/another/Exported.class");
        mojos.executeMojo(basedir.toFile(), "export-package");
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
        assertExportedPackages(basedir, "exported", "exported.another");

        // remove public class
        rm(basedir.toFile(), "target/classes/exported/another/Exported.class");
        mojos.executeMojo(basedir.toFile(), "export-package");
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
        assertExportedPackages(basedir, "exported");

        // remove last public class
        rm(basedir.toFile(), "target/classes/exported/Exported.class");
        mojos.executeMojo(basedir.toFile(), "export-package");
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
        assertExportedPackages(basedir, new String[0]);
    }

    @Test
    public void testNoClassesDirectory() throws Exception {
        Path basedir = resources.getBasedir("exportpackage/basic").toPath();
        mojos.executeMojo(basedir.toFile(), "export-package");
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
        assertExportedPackages(basedir, new String[0]);
    }

    @Test
    public void testBasic_symlinked() throws Exception {
        Assume.assumeTrue(Os.isFamily(Os.FAMILY_UNIX));

        Path basedir = resources.getBasedir().toPath();

        Path orig = basedir.resolve("orig");
        create(orig.toFile(), "target/classes/exported/Class.class");
        Path symlink = Files.createSymbolicLink(basedir.resolve("symlink"), orig);

        mojos.executeMojo(symlink.toFile(), "export-package");
        assertExportedPackages(symlink, "exported");
    }

    private void assertExportedPackages(Path basedir, String... exportedPackages) throws IOException {
        List<String> actual = Files.readAllLines(
                basedir.resolve("target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE), StandardCharsets.UTF_8);
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

    private void mkfile(Path basedir, String relpath) throws IOException {
        Path file = basedir.resolve(relpath);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }
}
