package io.takari.maven.plugins.sisu;

import static io.takari.maven.testing.TestResources.assertFileContents;
import static io.takari.maven.testing.TestResources.cp;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;

public class SisuIndexTest {
    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final IncrementalBuildRule mojos = new IncrementalBuildRule();

    public final TestProperties properties = new TestProperties();

    @Test
    public void testBasic() throws Exception {
        Path basedir = resources.getBasedir("sisu-index").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        addJavaxInjectDependency(project);

        mojos.executeMojo(project, "compile");
        mojos.executeMojo(project, "sisu-index");
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/META-INF/sisu/javax.inject.Named");
        assertSisuIndex(basedir, "classes", "sisu.Basic");

        // no-change rebuild
        mojos.executeMojo(project, "compile");
        mojos.executeMojo(project, "sisu-index");
        mojos.assertCarriedOverOutputs(basedir.toFile(), "target/classes/META-INF/sisu/javax.inject.Named");
        assertSisuIndex(basedir, "classes", "sisu.Basic");

        // introduce new type
        cp(basedir.toFile(), "src/main/java/sisu/Another.java-new", "src/main/java/sisu/Another.java");
        mojos.executeMojo(project, "compile");
        mojos.executeMojo(project, "sisu-index");
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/META-INF/sisu/javax.inject.Named");
        assertSisuIndex(basedir, "classes", "sisu.Another", "sisu.Basic"); // note alphabetical order

        // remove annotation from existing type
        cp(basedir.toFile(), "src/main/java/sisu/Basic.java-removed", "src/main/java/sisu/Basic.java");
        mojos.executeMojo(project, "compile");
        mojos.executeMojo(project, "sisu-index");
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/META-INF/sisu/javax.inject.Named");
        assertSisuIndex(basedir, "classes", "sisu.Another");
    }

    @Test
    public void testTestIndex() throws Exception {
        Path basedir = resources.getBasedir("sisu-index").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        addJavaxInjectDependency(project);

        mojos.executeMojo(project, "testCompile");
        mojos.executeMojo(project, "sisu-test-index");
        mojos.assertBuildOutputs(basedir.toFile(), "target/test-classes/META-INF/sisu/javax.inject.Named");
        assertSisuIndex(basedir, "test-classes", "sisu.BasicTest");
    }

    private void addJavaxInjectDependency(MavenProject project) throws Exception {
        mojos.newDependency(Path.of(properties.get("java-injext-jar")).toFile()).addTo(project);
    }

    private void assertSisuIndex(Path basedir, String output, String... types) throws IOException {
        StringBuilder expected = new StringBuilder();
        for (String type : types) {
            expected.append(type).append('\n');
        }
        assertFileContents(
                expected.toString(), basedir.toFile(), "target/" + output + "/" + SisuIndexMojo.PATH_SISU_INDEX);
    }
}
