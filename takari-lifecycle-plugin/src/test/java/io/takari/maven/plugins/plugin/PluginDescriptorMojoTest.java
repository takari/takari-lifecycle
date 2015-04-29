package io.takari.maven.plugins.plugin;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static io.takari.maven.testing.TestResources.assertFileContents;
import static io.takari.maven.testing.TestResources.cp;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;

public class PluginDescriptorMojoTest {
  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  public final TestProperties properties = new TestProperties();

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("plugin-descriptor/basic");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependencies(project, "apache-plugin-annotations-jar", "maven-plugin-api-jar");

    generatePluginDescriptor(project);

    mojos.assertBuildOutputs(basedir, "target/classes/META-INF/maven/plugin.xml", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(basedir, "expected/plugin.xml", "target/classes/META-INF/maven/plugin.xml");
  }

  private void generatePluginDescriptor(MavenProject project) throws Exception {
    mojos.executeMojo(project, "compile", newParameter("compilerId", "jdt"));
    mojos.executeMojo(project, "mojo-annotation-processor");
    mojos.executeMojo(project, "plugin-descriptor");
  }

  private void addDependencies(MavenProject project, String... keys) throws Exception {
    File[] files = new File[keys.length];
    for (int i = 0; i < keys.length; i++) {
      files[i] = new File(properties.get(keys[i]));
    }
    addDependencies(project, files);
  }

  private void addDependencies(MavenProject project, File... files) throws Exception {
    ArtifactHandler handler = mojos.getContainer().lookup(ArtifactHandler.class, "jar");
    Set<Artifact> artifacts = project.getArtifacts();
    for (File file : files) {
      DefaultArtifact artifact = new DefaultArtifact("test", file.getName(), "1.0", Artifact.SCOPE_COMPILE, "jar", null, handler);
      artifact.setFile(file);
      artifacts.add(artifact);
    }
    project.setArtifacts(artifacts);
  }

  @Test
  public void testInheritance_incremental() throws Exception {
    File basedir = resources.getBasedir("plugin-descriptor/incremental-inheritance");
    File abstractBasedir = new File(basedir, "abstract");
    File concreteBasedir = new File(basedir, "concrete");

    final MavenProject abstractProject = mojos.readMavenProject(abstractBasedir);
    addDependencies(abstractProject, "apache-plugin-annotations-jar", "maven-plugin-api-jar");
    final MavenProject concreteProject = mojos.readMavenProject(concreteBasedir);
    addDependencies(concreteProject, "apache-plugin-annotations-jar", "maven-plugin-api-jar");
    addDependencies(concreteProject, new File(abstractBasedir, "target/classes"));

    // initial build
    generatePluginDescriptor(abstractProject);
    mojos.assertBuildOutputs(abstractBasedir, "target/classes/META-INF/maven/plugin.xml", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(abstractBasedir, "expected/mojos.xml", "target/classes/META-INF/takari/mojos.xml");
    generatePluginDescriptor(concreteProject);
    mojos.assertBuildOutputs(concreteBasedir, "target/classes/META-INF/maven/plugin.xml", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(concreteBasedir, "expected/mojos.xml", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(concreteBasedir, "expected/plugin.xml", "target/classes/META-INF/maven/plugin.xml");

    // no change rebuild
    mojos.executeMojo(abstractProject, "plugin-descriptor");
    assertFileContents(abstractBasedir, "expected/mojos.xml", "target/classes/META-INF/takari/mojos.xml");
    mojos.executeMojo(concreteProject, "plugin-descriptor");
    assertFileContents(concreteBasedir, "expected/mojos.xml", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(concreteBasedir, "expected/plugin.xml", "target/classes/META-INF/maven/plugin.xml");

    // change external superclass
    cp(new File(abstractBasedir, "src/main/java/io/takari/lifecycle/uts/plugindescriptor"), "AbstractExternalMojo.java-changed", "AbstractExternalMojo.java");
    generatePluginDescriptor(abstractProject);
    assertFileContents(abstractBasedir, "expected/mojos.xml-changed", "target/classes/META-INF/takari/mojos.xml");
    generatePluginDescriptor(concreteProject);
    assertFileContents(concreteBasedir, "expected/mojos.xml-external-changed", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(concreteBasedir, "expected/plugin.xml-external-changed", "target/classes/META-INF/maven/plugin.xml");

    // remove annotations from external superclass
    cp(new File(abstractBasedir, "src/main/java/io/takari/lifecycle/uts/plugindescriptor"), "AbstractExternalMojo.java-removed", "AbstractExternalMojo.java");
    generatePluginDescriptor(abstractProject);
    generatePluginDescriptor(concreteProject);
    assertFileContents(concreteBasedir, "expected/mojos.xml-external-removed", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(concreteBasedir, "expected/plugin.xml-external-removed", "target/classes/META-INF/maven/plugin.xml");
  }

  @Test
  public void testIndirectReference_incremental() throws Exception {
    File basedir = resources.getBasedir("plugin-descriptor/incremental-indirect-reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependencies(project, "apache-plugin-annotations-jar", "maven-plugin-api-jar");

    generatePluginDescriptor(project);
    mojos.assertBuildOutputs(basedir, "target/classes/META-INF/maven/plugin.xml", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(basedir, "expected/plugin.xml", "target/classes/META-INF/maven/plugin.xml");

    cp(basedir, "src/main/java/io/takari/lifecycle/uts/plugindescriptor/IndirectReference.java-changed", "src/main/java/io/takari/lifecycle/uts/plugindescriptor/IndirectReference.java");
    generatePluginDescriptor(project);
    mojos.assertBuildOutputs(basedir, "target/classes/META-INF/maven/plugin.xml", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(basedir, "expected/plugin.xml-changed", "target/classes/META-INF/maven/plugin.xml");
  }

  @Test
  public void testJavadocInheritance_incremental() throws Exception {
    File basedir = resources.getBasedir("plugin-descriptor/incremental-javadoc-inheritance");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependencies(project, "apache-plugin-annotations-jar", "maven-plugin-api-jar");

    generatePluginDescriptor(project);
    mojos.assertBuildOutputs(basedir, "target/classes/META-INF/maven/plugin.xml", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(basedir, "expected/plugin.xml", "target/classes/META-INF/maven/plugin.xml");

    cp(basedir, "src/main/java/io/takari/lifecycle/uts/plugindescriptor/AbstractBasicMojo.java-changed", "src/main/java/io/takari/lifecycle/uts/plugindescriptor/AbstractBasicMojo.java");
    generatePluginDescriptor(project);
    mojos.assertBuildOutputs(basedir, "target/classes/META-INF/maven/plugin.xml", "target/classes/META-INF/takari/mojos.xml");
    assertFileContents(basedir, "expected/plugin.xml-changed", "target/classes/META-INF/maven/plugin.xml");
  }

  @Test
  public void testInheritance() throws Exception {
    // the point of the test is to assert parameters/requirements inheritance

    // test classes are named such that parent mojo is merged before child mojo
    // which causes duplicate parameters unless implementation handles this case

    File basedir = resources.getBasedir("plugin-descriptor/inheritance");
    MavenProject project = mojos.readMavenProject(basedir);
    addDependencies(project, "apache-plugin-annotations-jar", "maven-plugin-api-jar");
    generatePluginDescriptor(project);
    assertFileContents(basedir, "expected/plugin.xml", "target/classes/META-INF/maven/plugin.xml");
  }

  @Test
  public void testUnreadableDependencyMojosXml() throws Exception {
    // the point of this test is to assert the mojo does not throw exception when one of the dependencies cannot be read
    // such dependencies are ignored by the compiler, so plugin.xml generation should ignore them too

    File basedir = resources.getBasedir("plugin-descriptor/basic");
    File bogusJar = new File(basedir, "bogus.jar");
    new FileOutputStream(bogusJar).close();

    MavenProject project = mojos.readMavenProject(basedir);
    addDependencies(project, "apache-plugin-annotations-jar", "maven-plugin-api-jar");
    addDependencies(project, bogusJar);

    generatePluginDescriptor(project);
  }

  @Test
  public void testIncrementalDependencyChange() throws Exception {
    // the point of this test is to assert that non-structural dependency change does not force plugin.xml regeneration

    File basedir = resources.getBasedir("plugin-descriptor/incremental-dependency");
    File dependency = new File(basedir, "dependency");
    File plugin = new File(basedir, "plugin");

    mojos.executeMojo(dependency, "compile");

    MavenProject project = mojos.readMavenProject(plugin);
    addDependencies(project, "apache-plugin-annotations-jar", "maven-plugin-api-jar");
    addDependencies(project, new File(dependency, "target/classes"));
    generatePluginDescriptor(project);
    mojos.assertBuildOutputs(plugin, "target/classes/META-INF/maven/plugin.xml", "target/classes/META-INF/takari/mojos.xml");

    cp(dependency, "src/main/java/io/takari/lifecycle/uts/plugindescriptor/ComponentClass.java-private-change", "src/main/java/io/takari/lifecycle/uts/plugindescriptor/ComponentClass.java");
    mojos.executeMojo(dependency, "compile");
    mojos.assertBuildOutputs(dependency, "target/classes/io/takari/lifecycle/uts/plugindescriptor/ComponentClass.class");

    mojos.executeMojo(project, "compile", newParameter("compilerId", "jdt"));
    mojos.assertCarriedOverOutputs(plugin, "target/classes/io/takari/lifecycle/uts/plugindescriptor/BasicMojo.class");

    mojos.executeMojo(project, "mojo-annotation-processor");
    mojos.assertCarriedOverOutputs(plugin, "target/generated-sources/annotations/io.takari.lifecycle.uts.plugindescriptor.BasicMojo.mojo.xml");

    mojos.executeMojo(project, "plugin-descriptor");
    mojos.assertCarriedOverOutputs(plugin, "target/classes/META-INF/maven/plugin.xml", "target/classes/META-INF/takari/mojos.xml");
  }
}
