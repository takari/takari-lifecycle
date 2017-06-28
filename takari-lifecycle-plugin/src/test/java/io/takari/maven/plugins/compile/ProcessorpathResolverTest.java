package io.takari.maven.plugins.compile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.maven.testing.TestMavenRuntime;
import io.takari.maven.testing.TestResources;

public class ProcessorpathResolverTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final TestMavenRuntime maven = new TestMavenRuntime();

  @Test
  public void testVersionlessDependency() throws Exception {
    File basedir = resources.getBasedir();
    MavenProject project = maven.readMavenProject(basedir);
    maven.newDependency(temp.newFile()).setGroupId("g").setArtifactId("a").setVersion("1").addTo(project);

    MavenSession mavenSession = maven.newMavenSession(project);
    RepositorySystemSession repoSession = mavenSession.getRepositorySession();
    SimpleReactorReader.builder() //
        .addArtifact("g:a:1", temp.newFile()) //
        .addArtifact("g:a:pom:1", temp.newFile()) //
        .toSession(repoSession);

    ProcessorpathResolver resolver = maven.lookup(ProcessorpathResolver.class);
    List<File> processorpath = resolver.resolve(repoSession, project, dependencies("g:a"));

    assertEquals(1, processorpath.size());
    assertTrue(processorpath.get(0).isFile());
  }

  private List<Dependency> dependencies(String coords) {
    StringTokenizer st = new StringTokenizer(coords, ":");
    Dependency dependency = new Dependency();
    dependency.setGroupId(st.nextToken());
    dependency.setArtifactId(st.nextToken());
    return Collections.singletonList(dependency);
  }

}
