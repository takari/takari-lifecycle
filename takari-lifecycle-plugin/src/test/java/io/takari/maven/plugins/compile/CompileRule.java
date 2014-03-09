package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.io.File;
import java.util.Collection;
import java.util.Date;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;

public class CompileRule extends IncrementalBuildRule {

  private final long now = System.currentTimeMillis() - 10000;

  @Override
  public MavenSession newMavenSession(MavenProject project) {
    MavenSession session = super.newMavenSession(project);
    session.getRequest().setStartTime(new Date(now));
    return session;
  };

  public long getStartTime() {
    return now;
  }

  public File compile(File basedir, Xpp3Dom... parameters) throws Exception {
    MavenProject project = readMavenProject(basedir);
    compile(project, parameters);
    return basedir;
  }

  public void compile(MavenProject project, Xpp3Dom... parameters) throws Exception {
    MavenSession session = newMavenSession(project);
    MojoExecution execution = newMojoExecution();

    if (parameters != null) {
      Xpp3Dom configuration = execution.getConfiguration();
      for (Xpp3Dom parameter : parameters) {
        configuration.addChild(parameter);
      }
    }

    executeMojo(session, project, execution);
  }

  public MojoExecution newMojoExecution() {
    return newMojoExecution("compile-incremental");
  }

  public void assertMessageContains(File file, String... strings) throws Exception {
    Collection<String> messages = getBuildContextLog().getMessages(file);
    Assert.assertEquals(1, messages.size());
    String message = messages.iterator().next();
    for (String string : strings) {
      Assert.assertTrue("message contains " + string, message.contains(string));
    }
  }

  public void assertMessages(File basedir, String path, ErrorMessage expected) throws Exception {
    assertMessages(basedir, path, expected.getText());
  }

}
