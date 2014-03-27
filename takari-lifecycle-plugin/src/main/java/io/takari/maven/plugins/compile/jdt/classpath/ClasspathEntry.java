package io.takari.maven.plugins.compile.jdt.classpath;

import java.util.Collection;

import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public interface ClasspathEntry {

  Collection<String> getPackageNames();

  NameEnvironmentAnswer findType(String packageName, String binaryFileName);
}
