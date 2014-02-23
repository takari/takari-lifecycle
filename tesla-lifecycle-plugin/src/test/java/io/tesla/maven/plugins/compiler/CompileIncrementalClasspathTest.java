package io.tesla.maven.plugins.compiler;

import static org.apache.maven.plugin.testing.resources.TestResources.cp;

import java.io.File;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

public class CompileIncrementalClasspathTest extends AbstractCompileMojoTest {

  @Test
  public void testReactor() throws Exception {
    File parent = resources.getBasedir("compile-incremental-classpath/reactor-basic");

    compileReactor(parent);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");

    // no change rebuild
    compileReactor(parent);
    mojos.assertCarriedOverOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");

    // comment changes do NOT propagate
    cp(new File(parent, "module-b/src/main/java/reactor/moduleb"), "ModuleB.java-comment",
        "ModuleB.java");
    compileReactor(parent);
    mojos.assertCarriedOverOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");

    // API comment changes DO propagate
    cp(new File(parent, "module-b/src/main/java/reactor/moduleb"), "ModuleB.java-method",
        "ModuleB.java");
    compileReactor(parent);
    mojos.assertBuildOutputs(parent, "module-a/target/classes/reactor/modulea/ModuleA.class");
  }

  private void compileReactor(File parent) throws Exception {
    File moduleB = new File(parent, "module-b");

    compile(moduleB);

    MavenProject moduleA = mojos.readMavenProject(new File(parent, "module-a"));
    addDependency(moduleA, "module-b", new File(moduleB, "target/classes"));

    compile(moduleA);
  }

  private void compile(MavenProject project) throws Exception {
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = mojos.newMojoExecution("compile");
    mojos.executeMojo(session, project, execution);
  }

  private void addDependency(MavenProject project, String artifactId, File file) throws Exception {
    ArtifactHandler handler = mojos.getContainer().lookup(ArtifactHandler.class, "jar");
    DefaultArtifact artifact =
        new DefaultArtifact("test", artifactId, "1.0", Artifact.SCOPE_COMPILE, "jar", null, handler);
    artifact.setFile(file);
    Set<Artifact> artifacts = project.getArtifacts();
    artifacts.add(artifact);
    project.setArtifacts(artifacts);
  }
}
