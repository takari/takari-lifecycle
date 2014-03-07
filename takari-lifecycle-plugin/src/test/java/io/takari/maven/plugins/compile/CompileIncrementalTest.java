package io.takari.maven.plugins.compile;

import static org.apache.maven.plugin.testing.resources.TestResources.cp;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;

public class CompileIncrementalTest extends AbstractCompileTest {

  public CompileIncrementalTest(String compilerId, boolean fork) {
    super(compilerId, fork);
  }

  @Test
  public void testBasic() throws Exception {
    File basedir = compile("compile-incremental/basic");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");

    // no-change rebuild
    compile(basedir);
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), new String[0]);

    // change
    cp(basedir, "src/main/java/basic/Basic.java-modified", "src/main/java/basic/Basic.java");
    compile(basedir);
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "basic/Basic.class");
  }

  @Test
  public void testDelete() throws Exception {
    File basedir = compile("compile-incremental/delete");
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "delete/Delete.class",
        "delete/Keep.class");

    Assert.assertTrue(new File(basedir, "src/main/java/delete/Delete.java").delete());
    compile(basedir);
    mojos.assertBuildOutputs(new File(basedir, "target/classes"), "delete/Keep.class");
    mojos.assertDeletedOutputs(new File(basedir, "target/classes"), "delete/Delete.class");
  }

  @Test
  public void testError() throws Exception {
    String error =
        "ERROR Error.java [4:11] cannot find symbol\n  symbol:   class Errorr\n  location: class error.Error";

    File basedir = resources.getBasedir("compile-incremental/error");
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException e) {
      Assert.assertEquals("1 error(s) encountered, see previous message(s) for details",
          e.getMessage());
    }
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos.assertMessages(basedir, "src/main/java/error/Error.java", error);

    // no change rebuild, should still fail with the same error
    try {
      compile(basedir);
      Assert.fail();
    } catch (MojoExecutionException e) {
      Assert.assertEquals("1 error(s) encountered, see previous message(s) for details",
          e.getMessage());
    }
    mojos.assertBuildOutputs(basedir, new String[0]);
    mojos.assertMessages(basedir, "src/main/java/error/Error.java", error);

    // fixed the error should clear the message during next build
    cp(basedir, "src/main/java/error/Error.java-fixed", "src/main/java/error/Error.java");
    compile(basedir);
    mojos.assertBuildOutputs(basedir, "target/classes/error/Error.class");
  }

}
