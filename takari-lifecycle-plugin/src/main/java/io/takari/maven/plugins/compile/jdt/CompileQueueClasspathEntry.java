/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import io.takari.maven.plugins.compile.jdt.classpath.ClasspathEntry;
import io.takari.maven.plugins.compile.jdt.classpath.MutableClasspathEntry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;

class CompileQueueClasspathEntry implements ClasspathEntry, MutableClasspathEntry {

  // this is "live" reference to compile queue
  private final Set<ICompilationUnit> compileQueue;

  private Set<String> packageNames;

  private Map<String, ICompilationUnit> units;

  public CompileQueueClasspathEntry(Set<ICompilationUnit> compileQueue) {
    this.compileQueue = compileQueue;
    reset();
  }

  @Override
  public Collection<String> getPackageNames() {
    return packageNames;
  }

  @Override
  public NameEnvironmentAnswer findType(String packageName, String binaryFileName) {
    ICompilationUnit unit = units.get(packageName + "/" + binaryFileName);
    if (unit != null) {
      return new NameEnvironmentAnswer(unit, null);
    }
    return null;
  }

  @Override
  public void reset() {
    Set<String> packageNames = new HashSet<String>();
    Map<String, ICompilationUnit> units = new HashMap<String, ICompilationUnit>();
    for (ICompilationUnit unit : compileQueue) {
      String packageName = new String(CharOperation.concatWith(unit.getPackageName(), '/'));
      String binaryFileName = new String(unit.getMainTypeName()) + SuffixConstants.SUFFIX_STRING_CLASS;
      packageNames.add(packageName);
      units.put(packageName + "/" + binaryFileName, unit);
      // index empty packages
      int last = packageName.lastIndexOf('/');
      while (last > 0) {
        packageName = packageName.substring(0, last);
        packageNames.add(packageName);
        last = packageName.lastIndexOf('/');
      }
    }
    this.packageNames = Collections.unmodifiableSet(packageNames);
    this.units = Collections.unmodifiableMap(units);
  }

  @Override
  public String getEntryDescription() {
    return "#COMPILEQUEUE";
  }
}
