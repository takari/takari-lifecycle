package io.takari.maven.plugins.compile.jdt.classpath;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Set;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.AccessRule;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;

abstract class DependencyClasspathEntry implements ClasspathEntry {

  protected static final String PATH_EXPORT_PACKAGE = "META-INF/takari/export-package";

  protected final Set<String> packageNames;

  protected final Set<String> exportedPackages;

  protected DependencyClasspathEntry(Collection<String> packageNames, Collection<String> exportedPackages) {
    this.packageNames = ImmutableSet.copyOf(packageNames);
    this.exportedPackages = exportedPackages != null ? ImmutableSet.<String>copyOf(exportedPackages) : null;
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
    return CharStreams.readLines(new InputStreamReader(is, Charsets.UTF_8));
  }
}
