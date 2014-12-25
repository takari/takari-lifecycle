package io.takari.maven.plugins.compile.jdt;

import io.takari.maven.plugins.exportpackage.ExportPackageMojo;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Test;

public class CompileJdtClasspathVisibilityTest extends AbstractCompileJdtTest {

  private static final File DEPENDENCY = new File("src/test/projects/compile-jdt-classpath-visibility/test-dependency/test-dependency-0.1.jar");

  @Test
  public void testReference_directDependencyArtifact() throws Exception {
    // sanity check, references to classes from direct dependencies are allowed

    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "dependency", DEPENDENCY);

    compile(project, true);
  }

  @Test
  public void testReference_indirectDependencyArtifact() throws Exception {
    // references to classes from indirect dependencies fail the build

    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addIndirectDependency(project, "dependency", DEPENDENCY);

    compile(project);
    mojos.assertMessage(new File(basedir, "src/main/java/reference/Reference.java"), "The type 'DependencyClass' is not API", "test-dependency-0.1.jar");
  }

  @Test
  public void testReference_accessRulesViolationIgnore() throws Exception {
    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "dependency", DEPENDENCY);

    MavenSession session = mojos.newMavenSession(project);

    mojos.executeMojo(session, project, "compile", //
        param("compilerId", "jdt"), //
        param("transitiveDependencyReference", "ignore"), //
        param("privatePackageReference", "ignore"));
  }

  @Test
  public void testReference_testCompile() throws Exception {
    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "dependency", DEPENDENCY);

    MavenSession session = mojos.newMavenSession(project);

    Xpp3Dom[] params = new Xpp3Dom[] {param("compilerId", "jdt"), //
        param("transitiveDependencyReference", "error"), //
        param("privatePackageReference", "error")};

    mojos.executeMojo(session, project, "compile", params);

    mojos.executeMojo(session, project, "testCompile", params);
  }

  @Test
  public void testReference_testCompile_internal() throws Exception {
    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "dependency", DEPENDENCY);

    MavenSession session = mojos.newMavenSession(project);

    Xpp3Dom[] params = new Xpp3Dom[] {param("compilerId", "jdt"), //
        param("transitiveDependencyReference", "error"), //
        param("privatePackageReference", "error")};

    mojos.executeMojo(session, project, "compile", params);

    // make all main classes internal
    File exportsFile = new File(basedir, "target/classes/" + ExportPackageMojo.PATH_EXPORT_PACKAGE);
    exportsFile.getParentFile().mkdirs();
    new FileOutputStream(exportsFile).close();

    mojos.executeMojo(session, project, "testCompile", params);
  }


  @Test
  public void testIndirectGrandparent() throws Exception {
    // project -> ParentClass@direct-dependency -> DependencyClass@indirect-dependency

    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/indirect-grandparent");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "direct-dependency", new File(basedir, "test-parent/test-parent-0.1.jar"));
    addIndirectDependency(project, "indirect-dependency", DEPENDENCY);

    compile(project, true);
  }

  @Test
  public void testInternalReference() throws Exception {
    // project references classes that are not exported by the dependency (takari export-package)

    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/internal-reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "indirect-dependency", DEPENDENCY);

    compile(project);
    mojos.assertMessage(new File(basedir, "src/main/java/reference/Reference.java"), "The type 'DependencyInternalClass' is not API", "test-dependency-0.1.jar");
  }

  @Test
  public void testInternalReference_testCompile() throws Exception {
    // project references classes that are not exported by the dependency (takari export-package)

    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/internal-reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "indirect-dependency", DEPENDENCY);

    MavenSession session = mojos.newMavenSession(project);

    try {
      mojos.executeMojo(session, project, "testCompile", //
          param("compilerId", "jdt"), //
          param("transitiveDependencyReference", "error"), //
          param("privatePackageReference", "error"));
      Assert.fail();
    } catch (MojoExecutionException expected) {
      // expected
    }

    mojos.assertMessage(new File(basedir, "src/test/java/reference/ReferenceTest.java"), "The type 'DependencyInternalClass' is not API", "test-dependency-0.1.jar");
  }

  @Test
  public void testInternalReference_dependencyBundle() throws Exception {
    // project references classes that are not exported by the dependency (bundle manifest)

    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/internal-reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "indirect-dependency", new File("src/test/projects/compile-jdt-classpath-visibility/test-dependency-bundle/test-dependency-bundle-0.1.jar"));

    compile(project);
    mojos.assertMessage(new File(basedir, "src/main/java/reference/Reference.java"), "The type 'DependencyInternalClass' is not API", "test-dependency-bundle-0.1.jar");
  }

  private void addIndirectDependency(MavenProject project, String string, File file) throws Exception {
    addDependency(project, string, file, false);
  }

  private void compile(MavenProject project) throws Exception {
    compile(project, false);
  }

  private void compile(MavenProject project, boolean throwMojoExecutionException) throws Exception {
    try {
      mojos.compile(project, param("transitiveDependencyReference", "error"), param("privatePackageReference", "error"));
    } catch (MojoExecutionException e) {
      if (throwMojoExecutionException) {
        throw e;
      }
    }
  }

  private static Xpp3Dom param(String name, String value) {
    Xpp3Dom node = new Xpp3Dom(name);
    node.setValue(value);
    return node;
  }

}
