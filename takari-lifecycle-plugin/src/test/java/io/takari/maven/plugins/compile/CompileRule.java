package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.io.File;
import java.util.Collection;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;

public class CompileRule extends IncrementalBuildRule {

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
    return newMojoExecution("compile");
  }

  public void assertMessageContains(File file, String... strings) throws Exception {
    Collection<String> messages = getBuildContextLog().getMessages(file);
    Assert.assertEquals(1, messages.size());
    String message = messages.iterator().next();
    for (String string : strings) {
      Assert.assertTrue("message contains " + string, message.contains(string));
    }
  }

  public void assertMessage(File basedir, String path, ErrorMessage expected) throws Exception {
    Collection<String> messages = getBuildContextLog().getMessages(new File(basedir, path));
    Assert.assertEquals(messages.toString(), 1, messages.size());
    String message = messages.iterator().next();
    Assert.assertTrue(expected.isMatch(message));
  }

}
