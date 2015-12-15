/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

public class Classpath implements INameEnvironment {

  private final List<ClasspathEntry> entries;

  private final List<MutableClasspathEntry> mutableentries;

  private Multimap<String, ClasspathEntry> packages;

  public Classpath(List<ClasspathEntry> entries, List<MutableClasspathEntry> localentries) {
    this.entries = entries;
    this.mutableentries = localentries;
    this.packages = newPackageIndex(entries);
  }

  private static Multimap<String, ClasspathEntry> newPackageIndex(List<ClasspathEntry> entries) {
    Multimap<String, ClasspathEntry> classpath = LinkedHashMultimap.create();
    for (ClasspathEntry entry : entries) {
      for (String packageName : entry.getPackageNames()) {
        classpath.put(packageName, entry);
      }
    }
    return ImmutableMultimap.copyOf(classpath); // preserves order
  }

  @Override
  public NameEnvironmentAnswer findType(char[][] compoundTypeName) {
    if (compoundTypeName == null) {
      return null;
    }
    int typeNameIndex = compoundTypeName.length - 1;
    char[][] packageName = CharOperation.subarray(compoundTypeName, 0, typeNameIndex);
    return findType(new String(CharOperation.concatWith(packageName, '/')), new String(compoundTypeName[typeNameIndex]));
  }

  @Override
  public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
    return findType(new String(CharOperation.concatWith(packageName, '/')), new String(typeName));
  }

  private NameEnvironmentAnswer findType(String packageName, String typeName) {
    NameEnvironmentAnswer suggestedAnswer = null;
    Collection<ClasspathEntry> entries = !packageName.isEmpty() ? packages.get(packageName) : this.entries;
    if (entries != null) {
      for (ClasspathEntry entry : entries) {
        NameEnvironmentAnswer answer = entry.findType(packageName, typeName);
        if (answer != null) {
          if (!answer.ignoreIfBetter()) {
            if (answer.isBetter(suggestedAnswer)) {
              return answer;
            }
          } else if (answer.isBetter(suggestedAnswer)) {
            // remember suggestion and keep looking
            suggestedAnswer = answer;
          }
        }
      }
    }
    return suggestedAnswer;
  }

  @Override
  public boolean isPackage(char[][] parentPackageName, char[] packageName) {
    String name = new String(CharOperation.concatWith(parentPackageName, packageName, '/'));
    return packages.containsKey(name);
  }

  @Override
  public void cleanup() {
    // TODO
  }

  public void reset() {
    if (mutableentries == null) {
      return;
    }
    for (MutableClasspathEntry entry : mutableentries) {
      entry.reset();
    }
    packages = newPackageIndex(entries);
  }

  public List<ClasspathEntry> getEntries() {
    return entries;
  }
}
