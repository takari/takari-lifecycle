package io.takari.maven.plugins.compile.jdt;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;

import java.util.Collection;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.AccessRule;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

class ForbiddenClasspathEntry implements ClasspathEntry {
  private final ClasspathEntry entry;

  public ForbiddenClasspathEntry(ClasspathEntry entry) {
    this.entry = entry;
  }

  @Override
  public Collection<String> getPackageNames() {
    return entry.getPackageNames();
  }

  @Override
  public NameEnvironmentAnswer findType(String packageName, String binaryFileName) {
    NameEnvironmentAnswer answer = entry.findType(packageName, binaryFileName);
    if (answer == null) {
      return null;
    }
    AccessRule accessRule = new AccessRule(null /* pattern */, IProblem.ForbiddenReference, true /* keep looking for accessible type */);
    AccessRestriction accessRestriction = new AccessRestriction(accessRule, AccessRestriction.COMMAND_LINE, entry.getEntryName());
    // little yucky
    if (answer.getBinaryType() != null) {
      answer = new NameEnvironmentAnswer(answer.getBinaryType(), accessRestriction);
    } else if (answer.getSourceTypes() != null) {
      answer = new NameEnvironmentAnswer(answer.getSourceTypes(), accessRestriction);
    } else if (answer.getCompilationUnit() != null) {
      answer = new NameEnvironmentAnswer(answer.getCompilationUnit(), accessRestriction);
    } else {
      // TODO this is actually an error
    }

    return answer;
  }

  @Override
  public String getEntryName() {
    return entry.getEntryName();
  }
}
