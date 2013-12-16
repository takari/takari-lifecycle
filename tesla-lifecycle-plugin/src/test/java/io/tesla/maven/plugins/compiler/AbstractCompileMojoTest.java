package io.tesla.maven.plugins.compiler;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.tesla.incremental.maven.testing.AbstractBuildAvoidanceTest;
import org.eclipse.tesla.incremental.test.SpyBuildContextManager;
import org.junit.Assert;

public abstract class AbstractCompileMojoTest extends AbstractBuildAvoidanceTest {
  protected void compile(File basedir) throws Exception {
    MavenProject project = readMavenProject(basedir);
    MavenSession session = newMavenSession(project);
    MojoExecution execution = newMojoExecution("compile");
    executeMojo(session, project, execution);
  }

  protected void testCompile(File basedir) throws Exception {
    MavenProject project = readMavenProject(basedir);
    MavenSession session = newMavenSession(project);
    MojoExecution execution = newMojoExecution("testCompile");
    executeMojo(session, project, execution);
  }

  protected void touch(File basedir, String path) throws InterruptedException {
    touch(new File(basedir, path));
  }

  protected void touch(File file) throws InterruptedException {
    long now = System.currentTimeMillis();
    if (now - file.lastModified() < 1000L) {
      Thread.sleep(1000L);
    }
    file.setLastModified(System.currentTimeMillis());
  }

  protected void cp(File basedir, String from, String to) throws IOException, InterruptedException {
    File toFile = new File(basedir, to);
    long lastModified = toFile.lastModified();
    FileUtils.copyFile(new File(basedir, from), toFile);
    if (lastModified >= toFile.lastModified()) {
      touch(toFile);
    }
  }

  protected void rm(File basedir, String path) {
    Assert.assertTrue("delete " + path, new File(basedir, path).delete());
  }

  /**
   * Asserts specified paths were output during the build
   */
  protected static void assertBuildOutputs(File basedir, String... paths) {
    Set<File> expected = new TreeSet<File>();
    for (String path : paths) {
      expected.add(new File(basedir, path));
    }
    Set<File> actual = new TreeSet<File>(SpyBuildContextManager.getUpdatedOutputs());
    Assert.assertEquals(toString(expected), toString(actual));
  }

  protected static String toString(Collection<?> objects) {
    StringBuilder sb = new StringBuilder();
    for (Object file : objects) {
      sb.append(file.toString()).append('\n');
    }
    return sb.toString();
  }

  /**
   * Asserts specified output exists and is not older than specified input
   */
  protected static void assertBuildOutput(File basedir, String input, String output) {
    File inputFile = new File(basedir, input);
    File outputFile = new File(basedir, output);
    Assert.assertTrue("output is older than input",
        outputFile.lastModified() >= inputFile.lastModified());
  }

  protected File getCompiledBasedir(String location) throws Exception {
    final File basedir = super.getBasedir(location);
    compile(basedir);
    return basedir;
  }

}
