package io.takari.maven.plugins.sisu;

import static io.takari.maven.testing.TestResources.assertFileContents;
import static io.takari.maven.testing.TestResources.cp;

import java.io.File;
import java.io.IOException;

import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;

public class SisuIndexTest {
  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  public final TestProperties properties = new TestProperties();

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("sisu-index");
    MavenProject project = mojos.readMavenProject(basedir);
    addJavaxInjectDependency(project);

    mojos.executeMojo(project, "compile");
    mojos.executeMojo(project, "sisu-index");
    mojos.assertBuildOutputs(basedir, "target/classes/META-INF/sisu/javax.inject.Named");
    assertSisuIndex(basedir, "classes", "sisu.Basic");

    // no-change rebuild
    mojos.executeMojo(project, "compile");
    mojos.executeMojo(project, "sisu-index");
    mojos.assertCarriedOverOutputs(basedir, "target/classes/META-INF/sisu/javax.inject.Named");
    assertSisuIndex(basedir, "classes", "sisu.Basic");

    // introduce new type
    cp(basedir, "src/main/java/sisu/Another.java-new", "src/main/java/sisu/Another.java");
    mojos.executeMojo(project, "compile");
    mojos.executeMojo(project, "sisu-index");
    mojos.assertBuildOutputs(basedir, "target/classes/META-INF/sisu/javax.inject.Named");
    assertSisuIndex(basedir, "classes", "sisu.Another", "sisu.Basic"); // note alphabetical order

    // remove annotation from existing type
    cp(basedir, "src/main/java/sisu/Basic.java-removed", "src/main/java/sisu/Basic.java");
    mojos.executeMojo(project, "compile");
    mojos.executeMojo(project, "sisu-index");
    mojos.assertBuildOutputs(basedir, "target/classes/META-INF/sisu/javax.inject.Named");
    assertSisuIndex(basedir, "classes", "sisu.Another");
  }

  @Test
  public void testTestIndex() throws Exception {
    File basedir = resources.getBasedir("sisu-index");
    MavenProject project = mojos.readMavenProject(basedir);
    addJavaxInjectDependency(project);

    mojos.executeMojo(project, "testCompile");
    mojos.executeMojo(project, "sisu-test-index");
    mojos.assertBuildOutputs(basedir, "target/test-classes/META-INF/sisu/javax.inject.Named");
    assertSisuIndex(basedir, "test-classes", "sisu.BasicTest");
  }

  private void addJavaxInjectDependency(MavenProject project) throws Exception {
    mojos.newDependency(new File(properties.get("java-injext-jar"))).addTo(project);
  }

  private void assertSisuIndex(File basedir, String output, String... types) throws IOException {
    StringBuilder expected = new StringBuilder();
    for (String type : types) {
      expected.append(type).append('\n');
    }
    assertFileContents(expected.toString(), basedir, "target/" + output + "/" + SisuIndexMojo.PATH_SISU_INDEX);
  }
}
