package io.takari.maven.plugins.compile;

import java.io.File;
import java.util.Arrays;
import java.util.StringTokenizer;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.takari.maven.testing.TestResources;

@RunWith(Parameterized.class)
public abstract class AbstractCompileTest {

  public static final boolean isJava8orBetter;
  public static final boolean isJava9orBetter;
  public static final boolean isJava10orBetter;
  public static final boolean isJava11orBetter;

  static {
    boolean _isJava8orBetter = false;
    boolean _isJava9orBetter = false;
    boolean _isJava10orBetter = false;
    boolean _isJava11orBetter = false;

    String version = System.getProperty("java.specification.version");
    if (version != null) {
      StringTokenizer st = new StringTokenizer(version, ".");
      int major = Integer.parseInt(st.nextToken());
      if (major >= 11) {
        _isJava8orBetter = true;
        _isJava9orBetter = true;
        _isJava10orBetter = true;
        _isJava11orBetter = true;
      } else if (major >= 10) {
        _isJava8orBetter = true;
        _isJava9orBetter = true;
        _isJava10orBetter = true;
      } else if (major >= 9) {
        _isJava8orBetter = true;
        _isJava9orBetter = true;
      } else {
        int minor = Integer.parseInt(st.nextToken());
        _isJava8orBetter = minor >= 8;
      }
    }

    isJava8orBetter = _isJava8orBetter;
    isJava9orBetter = _isJava9orBetter;
    isJava10orBetter = _isJava10orBetter;
    isJava11orBetter = _isJava11orBetter;
  }

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule() {
    @Override
    public MojoExecution newMojoExecution(String goal, Xpp3Dom... parameters) {
      MojoExecution execution = super.newMojoExecution(goal, parameters);
      execution.getConfiguration().addChild(newParameter("compilerId", compilerId));
      return execution;
    };
  };

  protected final String compilerId;

  protected AbstractCompileTest(String compilerId) {
    this.compilerId = compilerId;
  }

  @Parameters(name = "{0}")
  public static Iterable<Object[]> compilers() {
    return Arrays.<Object[]>asList( //
        new Object[] {"javac"} //
        , new Object[] {"forked-javac"} //
        , new Object[] {"jdt"} //
    );
  }

  protected File compile(String name, Xpp3Dom... parameters) throws Exception {
    File basedir = resources.getBasedir(name);
    return mojos.compile(basedir, parameters);
  }

  protected File compile(File basedir, Xpp3Dom... parameters) throws Exception {
    return mojos.compile(basedir, parameters);
  }

  protected void addDependency(MavenProject project, String artifactId, File file) throws Exception {
    mojos.newDependency(file).setArtifactId(artifactId).addTo(project);
  }

  protected Xpp3Dom newParameter(String name, String value) {
    Xpp3Dom child = new Xpp3Dom(name);
    child.setValue(value);
    return child;
  }
}
