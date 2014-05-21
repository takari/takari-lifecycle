package io.takari.maven.plugins.compile;

import java.io.File;

import org.apache.maven.plugin.testing.resources.TestResources;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Rule;
import org.junit.Test;

public class EnabledPackagingTypesTest {
  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule();

  @Test
  public void test() throws Exception {
    File basedir = resources.getBasedir("compile/basic");

    Xpp3Dom type = new Xpp3Dom("type");
    type.setValue("custom");
    Xpp3Dom types = new Xpp3Dom("enabledPackagingTypes");
    types.addChild(type);

    mojos.compile(basedir, types);
    mojos.assertBuildOutputs(basedir, new String[0]);
  }
}
