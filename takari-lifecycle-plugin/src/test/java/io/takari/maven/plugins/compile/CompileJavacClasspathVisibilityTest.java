package io.takari.maven.plugins.compile;

import java.io.File;

import org.apache.maven.plugin.testing.resources.TestResources;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class CompileJavacClasspathVisibilityTest {
  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule();

  @Test
  public void testBasic() throws Exception {
    File basedir = resources.getBasedir("compile/basic");

    Xpp3Dom compilerId = new Xpp3Dom("compilerId");
    compilerId.setValue("javac");
    Xpp3Dom accessRulesViolation = new Xpp3Dom("accessRulesViolation");
    accessRulesViolation.setValue("error");
    try {
      mojos.compile(basedir, compilerId, accessRulesViolation);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      ErrorMessage.isMatch(e.getMessage(), "Compiler javac does not support accessRulesViolation=error, use compilerId=jdt");
    }
  }
}
