package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.io.File;
import java.util.Arrays;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompileTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule mojos = new IncrementalBuildRule();

  private final String compilerId;

  private final boolean fork;

  public CompileTest(String compilerId, boolean fork) {
    this.compilerId = compilerId;
    this.fork = fork;
  }

  @Parameters
  public static Iterable<Object[]> compilers() {
    return Arrays.asList( //
        new Object[] {"javac", Boolean.FALSE}, //
        new Object[] {"javac", Boolean.TRUE} //
        );
  }

  private File compile(String name) throws Exception {
    File basedir = resources.getBasedir(name);
    MavenProject project = mojos.readMavenProject(basedir);
    MavenSession session = mojos.newMavenSession(project);
    MojoExecution execution = mojos.newMojoExecution("compile-incremental");

    Xpp3Dom configuration = execution.getConfiguration();
    add(configuration, "fork", Boolean.toString(fork));

    mojos.executeMojo(session, project, execution);

    return basedir;
  }

  private void add(Xpp3Dom configuration, String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    configuration.addChild(child);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = compile("compile/basic");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }

  @Test
  public void testIncludes() throws Exception {
    File basedir = compile("compile/includes");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }

  @Test
  public void testExcludes() throws Exception {
    File basedir = compile("compile/excludes");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }
}
