package io.takari.maven.plugins.compile;

import java.io.File;
import java.util.Collection;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.ComparisonFailure;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.plugins.compile.jdt.ClasspathDigester;
import io.takari.maven.plugins.compile.jdt.ClasspathEntryCache;

public class CompileRule extends IncrementalBuildRule {

  public File compile(File basedir, Xpp3Dom... parameters) throws Exception {
    MavenProject project = readMavenProject(basedir);
    compile(project, parameters);
    return basedir;
  }

  public void compile(MavenProject project, Xpp3Dom... parameters) throws Exception {
    MavenSession session = newMavenSession(project);
    compile(session, project, parameters);
  }

  public void compile(MavenSession session, MavenProject project, Xpp3Dom... parameters) throws Exception {
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

  public void assertMessage(File file, String... strings) throws Exception {
    Collection<String> messages = getBuildContextLog().getMessages(file);
    Assert.assertEquals(messages.toString(), 1, messages.size());
    String message = messages.iterator().next();
    Assert.assertTrue(ErrorMessage.isMatch(message, strings));
  }

  public void assertMessage(File basedir, String path, ErrorMessage expected) throws Exception {
    Collection<String> messages = getBuildContextLog().getMessages(new File(basedir, path));
    if (messages.size() != 1) {
      throw new ComparisonFailure("Number of messages", expected.toString(), messages.toString());
    }
    String message = messages.iterator().next();
    if (!expected.isMatch(message)) {
      throw new ComparisonFailure("", expected.toString(), message);
    }
  }

  public void flushClasspathCaches() {
    ClasspathEntryCache.flush();
    ProjectClasspathDigester.flush();
    ClasspathDigester.flush();
  }
}
