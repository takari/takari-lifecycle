package io.takari.maven.plugins.compile;

import static io.takari.maven.testing.TestMavenRuntime.newParameter;

import java.io.File;

import org.apache.maven.project.MavenProject;
import org.junit.Rule;
import org.junit.Test;

import io.takari.maven.testing.TestResources;

public class MixedCompilerBackendTest {
  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule();

  @Test
  public void testClasspath() throws Exception {
    // assert classpath caches are updated when project classes/test-classes changes

    File basedir = resources.getBasedir("compile-incremental/classpath");
    File moduleB = new File(basedir, "module-b");
    File moduleA = new File(basedir, "module-a");

    mojos.compile(moduleB, newParameter("proc", "only"), newParameter("compilerId", "jdt"));
    mojos.assertBuildOutputs(moduleB, new String[0]);
    mojos.compile(moduleB, newParameter("compilerId", "javac"));
    mojos.assertBuildOutputs(moduleB, "target/classes/moduleb/ModuleB.class");

    MavenProject projectA = mojos.readMavenProject(moduleA);
    mojos.newDependency(new File(moduleB, "target/classes")).addTo(projectA);
    mojos.compile(projectA, newParameter("compilerId", "jdt"));
    mojos.assertBuildOutputs(moduleA, "target/classes/modulea/ModuleA.class");
  }
}
