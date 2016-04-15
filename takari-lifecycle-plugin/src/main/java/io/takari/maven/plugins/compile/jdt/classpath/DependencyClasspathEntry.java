package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.AccessRule;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.osgi.framework.util.Headers;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.common.io.LineProcessor;

import io.takari.maven.plugins.exportpackage.ExportPackageMojo;

public abstract class DependencyClasspathEntry implements ClasspathEntry {

  protected static final String PATH_EXPORT_PACKAGE = ExportPackageMojo.PATH_EXPORT_PACKAGE;

  protected static final String PATH_MANIFESTMF = "META-INF/MANIFEST.MF";

  protected final File file;

  protected final Set<String> packageNames;

  protected final Set<String> exportedPackages;

  protected DependencyClasspathEntry(File file, Collection<String> packageNames, Collection<String> exportedPackages) {
    this.file = normalize(file);
    this.packageNames = ImmutableSet.copyOf(packageNames);
    this.exportedPackages = exportedPackages != null ? ImmutableSet.<String>copyOf(exportedPackages) : null;
  }

  private static File normalize(File file) {
    try {
      return file.getCanonicalFile();
    } catch (IOException e) {
      // should not happen as we know that the file exists
      return file.getAbsoluteFile();
    }
  }

  protected AccessRestriction getAccessRestriction(String packageName) {
    if (exportedPackages != null && !exportedPackages.contains(packageName)) {
      AccessRule rule = new AccessRule(null /* pattern */, IProblem.ForbiddenReference, true /* keep looking for accessible type */);
      return new AccessRestriction(rule, AccessRestriction.COMMAND_LINE, getEntryName());
    }
    return null;
  }

  @Override
  public Collection<String> getPackageNames() {
    return packageNames;
  }

  protected static Collection<String> parseExportPackage(InputStream is) throws IOException {
    LineProcessor<List<String>> processor = new LineProcessor<List<String>>() {
      final List<String> result = new ArrayList<String>();

      @Override
      public boolean processLine(String line) throws IOException {
        result.add(line.replace('.', '/'));
        return true; // keep reading
      }

      @Override
      public List<String> getResult() {
        return result;
      }
    };
    return CharStreams.readLines(new InputStreamReader(is, Charsets.UTF_8), processor);
  }

  protected static Collection<String> parseBundleManifest(InputStream is) throws IOException, BundleException {
    Headers<String, String> headers = Headers.parseManifest(is);
    if (!headers.containsKey(Constants.BUNDLE_SYMBOLICNAME)) {
      return null; // not an OSGi bundle
    }
    String exportPackageHeader = headers.get(Constants.EXPORT_PACKAGE);
    if (exportPackageHeader == null) {
      return ImmutableSet.of(); // nothing is exported
    }
    Set<String> packages = new HashSet<>();
    for (ManifestElement element : ManifestElement.parseHeader(Constants.EXPORT_PACKAGE, exportPackageHeader)) {
      packages.add(element.getValue().replace('.', '/'));
    }
    return packages;
  }

  @Override
  public String getEntryDescription() {
    StringBuilder sb = new StringBuilder(getEntryName());
    if (exportedPackages != null) {
      sb.append("[");
      int idx = 0;
      for (String exportedPackage : exportedPackages) {
        if (idx++ > 0) {
          sb.append(File.pathSeparatorChar);
        }
        sb.append('+').append(exportedPackage).append("/*");
      }
      if (idx > 0) {
        sb.append(File.pathSeparatorChar);
      }
      sb.append("?**/*");
      sb.append(']');
    }
    return sb.toString();
  }

  @Override
  public NameEnvironmentAnswer findType(String packageName, String typeName) {
    return findType(packageName, typeName, getAccessRestriction(packageName));
  }

  public abstract NameEnvironmentAnswer findType(String packageName, String typeName, AccessRestriction accessRestriction);

  public String getEntryName() {
    return file.getAbsolutePath();
  }
}
