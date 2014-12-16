package io.takari.maven.plugins.compile.jdt;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
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
    // project references classes that are not exported by the dependency

    File basedir = resources.getBasedir("compile-jdt-classpath-visibility/internal-reference");

    MavenProject project = mojos.readMavenProject(basedir);
    addDependency(project, "indirect-dependency", DEPENDENCY);

    compile(project);
    mojos.assertMessage(new File(basedir, "src/main/java/reference/Reference.java"), "The type 'DependencyInternalClass' is not API", "test-dependency-0.1.jar");
  }

  private void addIndirectDependency(MavenProject project, String string, File file) throws Exception {
    addDependency(project, string, file, false);
  }

  private void compile(MavenProject project) throws Exception {
    compile(project, false);
  }

  private void compile(MavenProject project, boolean throwMojoExecutionException) throws Exception {
    Xpp3Dom accessRulesViolation = new Xpp3Dom("accessRulesViolation");
    accessRulesViolation.setValue("error");
    try {
      mojos.compile(project, accessRulesViolation);
    } catch (MojoExecutionException e) {
      if (throwMojoExecutionException) {
        throw e;
      }
    }
  }

}
