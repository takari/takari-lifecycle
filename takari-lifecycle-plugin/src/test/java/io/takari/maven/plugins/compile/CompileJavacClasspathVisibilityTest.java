package io.takari.maven.plugins.compile;

import io.takari.maven.testing.TestResources;

import java.io.File;

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
    Xpp3Dom transitiveDependencyReference = new Xpp3Dom("transitiveDependencyReference");
    transitiveDependencyReference.setValue("error");
    Xpp3Dom privatePackageReference = new Xpp3Dom("privatePackageReference");
    privatePackageReference.setValue("error");

    try {
      mojos.compile(basedir, compilerId, transitiveDependencyReference);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      ErrorMessage.isMatch(e.getMessage(), "Compiler javac does not support transitiveDependencyReference=error, use compilerId=jdt");
    }

    try {
      mojos.compile(basedir, compilerId, privatePackageReference);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      ErrorMessage.isMatch(e.getMessage(), "Compiler javac does not support privatePackageReference=error, use compilerId=jdt");
    }

  }
}
