package adhoc;

import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;
import io.takari.maven.plugins.compile.jdt.CompilerJdt;

import java.io.*;
import java.util.*;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.codehaus.plexus.util.DirectoryScanner;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;

public class JDT {
  public static void main(String[] args) throws Exception {
    Stopwatch stopwatch = new Stopwatch().start();

    File state = new File("/var/tmp/incremental/sfdc.state");
    Map<String, Serializable> config = Collections.emptyMap();
    DefaultBuildContext<Exception> context = new DefaultBuildContext<Exception>(state, config) {
      @Override
      protected Exception newBuildFailureException(int errorCount) {
        return new Exception();
      }

      @Override
      protected void logMessage(Object inputResource, int line, int column, String message,
          Severity severity, Throwable cause) {
        if (severity == Severity.ERROR) {
          System.out.format("%s: %s\n", inputResource, message);
        }
      }
    };

    File basedir = new File("/Users/ifedorenko/dev/sandbox/butc-maven/core/sfdc");

    CompilerJdt compiler = new CompilerJdt(context);
    compiler.setOutputDirectory(new File(basedir, "target/classes"));
    compiler.setSource("1.6");
    compiler.setTarget("1.6");
    compiler.setProc(Proc.none);
    compiler.setGeneratedSourcesDirectory(null);
    compiler.setAnnotationProcessors(null);
    compiler.setVerbose(false);
    compiler.setPom(new File(basedir, "pom.xml"));
    compiler.setSourceEncoding(null);
    compiler.setSourceRoots(getSourceRoots());

    stopwatch.reset();
    stopwatch.start();
    compiler.setClasspath(getClasspath());
    System.err.println("classpath: " + stopwatch.toString());

    // stopwatch.reset();
    // stopwatch.start();
    // compiler.compile();

    System.err.println(stopwatch.toString());
  }

  private static List<File> getSources() throws IOException {
    List<File> sources = new ArrayList<File>();
    for (String sourcePath : getSourceRoots()) {
      File sourceRoot = new File(sourcePath.trim());
      if (!sourceRoot.isDirectory()) {
        continue;
      }
      DirectoryScanner scanner = new DirectoryScanner();
      scanner.setBasedir(sourceRoot);
      // TODO this is a bug in project model, includes/excludes should be per sourceRoot
      Set<String> includes = Collections.singleton("**/*.java");
      scanner.setIncludes(includes.toArray(new String[includes.size()]));
      scanner.scan();
      String[] includedFiles = scanner.getIncludedFiles();
      for (String relpath : includedFiles) {
        sources.add(new File(sourceRoot, relpath));
      }
    }
    return sources;
  }

  private static List<Artifact> getClasspath() throws IOException {
    List<Artifact> result = new ArrayList<Artifact>();
    for (String path : Files.readLines(new File("/var/tmp/incremental/classpath"), Charsets.UTF_8)) {
      File file = new File(path.trim());
      Artifact artifact =
          new DefaultArtifact("test", file.getName(), "1", "compiler", "jar", null,
              new DefaultArtifactHandlerStub("jar"));
      artifact.setFile(file);
      result.add(artifact);
    }
    return result;
  }

  private static Set<String> getSourceRoots() throws IOException {
    return new LinkedHashSet<String>(Files.readLines(new File("/var/tmp/incremental/sourceroots"),
        Charsets.UTF_8));
  }
}
