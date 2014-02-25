package io.takari.maven.plugins.compiler.javac;

/**
 * The MIT License
 *
 * Copyright (c) 2005, The Codehaus
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import io.takari.maven.plugins.compiler.api.CompilerConfiguration;
import io.takari.maven.plugins.compiler.javac.JavacCompiler;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.util.StringUtils;

/**
 * @author <a href="mailto:jason@plexus.org">Jason van Zyl</a>
 */
public abstract class AbstractJavacCompilerTest extends AbstractCompilerTest {
  private static final String PS = File.pathSeparator;

  public void setUp() throws Exception {
    super.setUp();
    setCompilerDebug(true);
    setCompilerDeprecationWarnings(true);

  }

  protected String getRoleHint() {
    return "javac";
  }

  protected int expectedErrors() {

    // javac output changed for misspelled modifiers starting in 1.6...they now generate 2 errors per occurrence, not one.
    if ("1.5".compareTo(getJavaVersion()) < 0) {
      return 4;
    } else {
      return 3;
    }
  }

  protected int expectedWarnings() {
    return 2;
  }

  protected Collection<String> expectedOutputFiles() {
    return Arrays.asList(new String[] {
        "org/codehaus/foo/Deprecation.class", "org/codehaus/foo/ExternalDeps.class", "org/codehaus/foo/Person.class", "org/codehaus/foo/ReservedWord.class"
    });
  }

  public void internalTest(CompilerConfiguration compilerConfiguration, List<String> expectedArguments) {
    String[] actualArguments = JavacCompiler.buildCompilerArguments(compilerConfiguration, new String[0]);

    assertEquals("The expected and actual argument list sizes differ.", expectedArguments.size(), actualArguments.length);

    for (int i = 0; i < actualArguments.length; i++) {
      assertEquals("Unexpected argument", expectedArguments.get(i), actualArguments[i]);
    }
  }

  public void testBuildCompilerArgs13() {
    List<String> expectedArguments = new ArrayList<String>();

    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();

    compilerConfiguration.setCompilerVersion("1.3");

    populateArguments(compilerConfiguration, expectedArguments, true, true);

    internalTest(compilerConfiguration, expectedArguments);
  }

  public void testBuildCompilerArgs14() {
    List<String> expectedArguments = new ArrayList<String>();

    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();

    compilerConfiguration.setCompilerVersion("1.4");

    populateArguments(compilerConfiguration, expectedArguments, false, false);

    internalTest(compilerConfiguration, expectedArguments);
  }

  public void testBuildCompilerArgs15() {
    List<String> expectedArguments = new ArrayList<String>();

    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();

    compilerConfiguration.setCompilerVersion("1.5");

    populateArguments(compilerConfiguration, expectedArguments, false, false);

    internalTest(compilerConfiguration, expectedArguments);
  }

  public void testBuildCompilerArgsUnspecifiedVersion() {
    List<String> expectedArguments = new ArrayList<String>();

    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();

    populateArguments(compilerConfiguration, expectedArguments, false, false);

    internalTest(compilerConfiguration, expectedArguments);
  }

  public void testBuildCompilerDebugLevel() {
    List<String> expectedArguments = new ArrayList<String>();

    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();

    compilerConfiguration.setDebug(true);

    compilerConfiguration.setDebugLevel("none");

    populateArguments(compilerConfiguration, expectedArguments, false, false);

    internalTest(compilerConfiguration, expectedArguments);
  }

  // PLXCOMP-190
  public void testJRuntimeArguments() {
    List<String> expectedArguments = new ArrayList<String>();

    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();

    // outputLocation
    compilerConfiguration.setOutputLocation("/output");
    expectedArguments.add("-d");
    expectedArguments.add(new File("/output").getAbsolutePath());

    // targetVersion
    compilerConfiguration.setTargetVersion("1.3");
    expectedArguments.add("-target");
    expectedArguments.add("1.3");

    // sourceVersion
    compilerConfiguration.setSourceVersion("1.3");
    expectedArguments.add("-source");
    expectedArguments.add("1.3");

    // customCompilerArguments
    Map<String, String> customCompilerArguments = new LinkedHashMap<String, String>();
    customCompilerArguments.put("-J-Duser.language=en_us", null);
    compilerConfiguration.setCustomCompilerArgumentsAsMap(customCompilerArguments);
    // don't expect this argument!!

    internalTest(compilerConfiguration, expectedArguments);
  }

  /*
   * This test fails on Java 1.4. The multiple parameters of the same source file cause an error, as it is interpreted as a DuplicateClass Setting the size of the array to 3 is fine, but does not
   * exactly test what it is supposed to - disabling the test for now public void testCommandLineTooLongWhenForking() throws Exception { JavacCompiler compiler = (JavacCompiler) lookup(
   * org.codehaus.plexus.compiler.Compiler.ROLE, getRoleHint() );
   * 
   * File destDir = new File( "target/test-classes-cmd" ); destDir.mkdirs();
   * 
   * // fill the cmd line arguments, 300 is enough to make it break String[] args = new String[400]; args[0] = "-d"; args[1] = destDir.getAbsolutePath(); for ( int i = 2; i < args.length; i++ ) {
   * args[i] = "org/codehaus/foo/Person.java"; }
   * 
   * CompilerConfiguration config = new CompilerConfiguration(); config.setWorkingDirectory( new File( getBasedir() + "/src/test-input/src/main" ) ); config.setFork( true );
   * 
   * List messages = compiler.compileOutOfProcess( config, "javac", args );
   * 
   * assertEquals( "There were errors launching the external compiler: " + messages, 0, messages.size() ); }
   */

  private void populateArguments(CompilerConfiguration compilerConfiguration, List<String> expectedArguments, boolean suppressSourceVersion, boolean suppressEncoding) {
    // outputLocation

    compilerConfiguration.setOutputLocation("/output");

    expectedArguments.add("-d");

    expectedArguments.add(new File("/output").getAbsolutePath());

    // classpathEntires

    List<String> classpathEntries = new ArrayList<String>();

    classpathEntries.add("/myjar1.jar");

    classpathEntries.add("/myjar2.jar");

    compilerConfiguration.setClasspathEntries(classpathEntries);

    expectedArguments.add("-classpath");

    expectedArguments.add("/myjar1.jar" + PS + "/myjar2.jar" + PS);

    // sourceRoots

    List<String> compileSourceRoots = new ArrayList<String>();

    compileSourceRoots.add("/src/main/one");

    compileSourceRoots.add("/src/main/two");

    compilerConfiguration.setSourceLocations(compileSourceRoots);

    //expectedArguments.add("-sourcepath");

    //expectedArguments.add("/src/main/one" + PS + "/src/main/two" + PS);

    // debug

    compilerConfiguration.setDebug(true);

    if (StringUtils.isNotEmpty(compilerConfiguration.getDebugLevel())) {
      expectedArguments.add("-g:" + compilerConfiguration.getDebugLevel());
    } else {
      expectedArguments.add("-g");
    }

    // showDeprecation

    compilerConfiguration.setShowDeprecation(true);

    expectedArguments.add("-deprecation");

    // targetVersion

    compilerConfiguration.setTargetVersion("1.3");

    expectedArguments.add("-target");

    expectedArguments.add("1.3");

    // sourceVersion

    compilerConfiguration.setSourceVersion("1.3");

    if (!suppressSourceVersion) {
      expectedArguments.add("-source");

      expectedArguments.add("1.3");
    }

    // sourceEncoding

    compilerConfiguration.setSourceEncoding("iso-8859-1");

    if (!suppressEncoding) {
      expectedArguments.add("-encoding");

      expectedArguments.add("iso-8859-1");
    }

    // customerCompilerArguments

    Map<String, String> customerCompilerArguments = new LinkedHashMap<String, String>();

    customerCompilerArguments.put("arg1", null);

    customerCompilerArguments.put("foo", "bar");

    compilerConfiguration.setCustomCompilerArgumentsAsMap(customerCompilerArguments);

    expectedArguments.add("arg1");

    expectedArguments.add("foo");

    expectedArguments.add("bar");
  }
}
