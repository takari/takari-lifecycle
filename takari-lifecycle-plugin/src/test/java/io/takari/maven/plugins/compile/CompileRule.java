package io.takari.maven.plugins.compile;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;

import java.util.Date;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

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

}
