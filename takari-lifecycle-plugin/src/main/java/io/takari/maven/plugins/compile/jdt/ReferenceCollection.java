/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Tim Hanson <thanson@bea.com> - fix for https://bugs.eclipse.org/bugs/show_bug.cgi?id=137634
 *     Takari, Inc - adopted for use in takari-lifecycle-plugin
 *******************************************************************************/
package io.takari.maven.plugins.compile.jdt;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jdt.core.compiler.BuildContext;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.core.builder.NameSet;
import org.eclipse.jdt.internal.core.builder.QualifiedNameSet;

import com.google.common.collect.ImmutableSet;


// adopted from org.eclipse.jdt.internal.core.builder.ReferenceCollection because we need Serializable
class ReferenceCollection implements Serializable {

  // contains no simple names as in just 'a' which is kept in simpleNameReferences instead
  final Set<String> qualifiedNameReferences;
  final Set<String> simpleNameReferences;
  final Set<String> rootReferences;

  public ReferenceCollection(char[][] rootReferences, char[][][] qualifiedNameReferences, char[][] simpleNameReferences) {
    this.qualifiedNameReferences = ImmutableSet.copyOf(toStringSet(qualifiedNameReferences));
    this.simpleNameReferences = ImmutableSet.copyOf(toStringSet(simpleNameReferences));
    this.rootReferences = ImmutableSet.copyOf(toStringSet(rootReferences));
  }

  public ReferenceCollection() {
    this.qualifiedNameReferences = new LinkedHashSet<>();
    this.simpleNameReferences = new LinkedHashSet<>();
    this.rootReferences = new LinkedHashSet<>();
  }

  private Set<String> toStringSet(char[][] strings) {
    Set<String> set = new LinkedHashSet<String>();
    for (char[] string : strings) {
      set.add(new String(string));
    }
    return set;
  }

  private Set<String> toStringSet(char[][][] arrays) {
    Set<String> set = new LinkedHashSet<String>();
    for (char[][] array : arrays) {
      set.add(CharOperation.toString(array));
    }
    return set;
  }

  public boolean includes(Collection<String> qualifiedNames, Collection<String> simpleNames, Collection<String> rootNames) {

    if (rootNames != null) {
      boolean foundRoot = false;
      for (String rootName : rootNames) {
        foundRoot = rootReferences.contains(rootName);
        if (foundRoot) {
          break;
        }
      }
      if (!foundRoot) {
        return false;
      }
    }

    for (String simpleName : simpleNames) {
      if (simpleNameReferences.contains(simpleName)) {
        for (String qualifiedName : qualifiedNames) {
          if (qualifiedName.indexOf('.') > 0 ? qualifiedNameReferences.contains(qualifiedName) : simpleNameReferences.contains(qualifiedName)) {
            return true;
          }
        }
        return false;
      }
    }

    return false;
  }

  //
  // the code below does unnecessary String<->char[] conversion (sloppy copy&paste job)
  // TODO decide if we want to fully switch to String or go back to char[] (the latter will be almost vanilla jdt source)
  //

  /**
   * Adds the fully-qualified type names of any new dependencies, each name is of the form "p1.p2.A.B".
   * 
   * @see BuildContext#recordDependencies(String[])
   */
  public void addDependencies(Collection<String> typeNameDependencies) {
    // if each qualified type name is already known then all of its subNames can be skipped
    // and its expected that very few qualified names in typeNameDependencies need to be added
    // but could always take 'p1.p2.p3.X' and make all qualified names 'p1' 'p1.p2' 'p1.p2.p3' 'p1.p2.p3.X', then intern
    char[][][] qNames = new char[typeNameDependencies.size()][][];
    Iterator<String> typeNameDependency = typeNameDependencies.iterator();
    for (int i = 0; typeNameDependency.hasNext(); i++) {
      qNames[i] = CharOperation.splitOn('.', typeNameDependency.next().toCharArray());
    }
    qNames = internQualifiedNames(qNames, false);

    next: for (int i = qNames.length; --i >= 0;) {
      char[][] qualifiedTypeName = qNames[i];
      while (!includes(qualifiedTypeName)) {
        if (!includes(qualifiedTypeName[qualifiedTypeName.length - 1])) {
          this.simpleNameReferences.add(new String(qualifiedTypeName[qualifiedTypeName.length - 1]));
        }
        if (!insideRoot(qualifiedTypeName[0])) {
          this.rootReferences.add(new String(qualifiedTypeName[0]));
        }
        this.qualifiedNameReferences.add(CharOperation.toString(qualifiedTypeName));

        qualifiedTypeName = CharOperation.subarray(qualifiedTypeName, 0, qualifiedTypeName.length - 1);
        char[][][] temp = internQualifiedNames(new char[][][] {qualifiedTypeName}, false);
        if (temp == EmptyQualifiedNames) continue next; // qualifiedTypeName is a well known name
        qualifiedTypeName = temp[0];
      }
    }
  }

  private static char[][][] internQualifiedNames(char[][][] qualifiedNames, boolean keepWellKnown) {
    if (qualifiedNames == null) return EmptyQualifiedNames;
    int length = qualifiedNames.length;
    if (length == 0) return EmptyQualifiedNames;

    char[][][] keepers = new char[length][][];
    int index = 0;
    next: for (int i = 0; i < length; i++) {
      char[][] qualifiedName = qualifiedNames[i];
      int qLength = qualifiedName.length;
      for (int j = 0, m = WellKnownQualifiedNames.length; j < m; j++) {
        char[][] wellKnownName = WellKnownQualifiedNames[j];
        if (qLength > wellKnownName.length) break; // all remaining well known names are shorter
        if (CharOperation.equals(qualifiedName, wellKnownName)) {
          if (keepWellKnown) {
            keepers[index++] = wellKnownName;
          }
          continue next;
        }
      }

      // InternedQualifiedNames[0] is for the rest (> 7 & 1)
      // InternedQualifiedNames[1] is for size 2...
      // InternedQualifiedNames[6] is for size 7
      QualifiedNameSet internedNames = InternedQualifiedNames[qLength <= MaxQualifiedNames ? qLength - 1 : 0];
      qualifiedName = internSimpleNames(qualifiedName, false);
      keepers[index++] = internedNames.add(qualifiedName);
    }
    if (length > index) {
      if (index == 0) return EmptyQualifiedNames;
      System.arraycopy(keepers, 0, keepers = new char[index][][], 0, index);
    }
    return keepers;
  }

  private static char[][] internSimpleNames(char[][] simpleNames, boolean removeWellKnown) {
    if (simpleNames == null) return EmptySimpleNames;
    int length = simpleNames.length;
    if (length == 0) return EmptySimpleNames;

    char[][] keepers = new char[length][];
    int index = 0;
    next: for (int i = 0; i < length; i++) {
      char[] name = simpleNames[i];
      int sLength = name.length;
      for (int j = 0, m = WellKnownSimpleNames.length; j < m; j++) {
        char[] wellKnownName = WellKnownSimpleNames[j];
        if (sLength > wellKnownName.length) break; // all remaining well known names are shorter
        if (CharOperation.equals(name, wellKnownName)) {
          if (!removeWellKnown) keepers[index++] = WellKnownSimpleNames[j];
          continue next;
        }
      }

      // InternedSimpleNames[0] is for the rest (> 29)
      // InternedSimpleNames[1] is for size 1...
      // InternedSimpleNames[29] is for size 29
      NameSet internedNames = InternedSimpleNames[sLength < MaxSimpleNames ? sLength : 0];
      keepers[index++] = internedNames.add(name);
    }
    if (length > index) {
      if (index == 0) return EmptySimpleNames;
      System.arraycopy(keepers, 0, keepers = new char[index][], 0, index);
    }
    return keepers;
  }

  private boolean includes(char[][] qualifiedName) {
    return this.qualifiedNameReferences.contains(CharOperation.toString(qualifiedName));
  }

  private boolean includes(char[] simpleName) {
    return this.simpleNameReferences.contains(new String(simpleName));
  }

  private boolean insideRoot(char[] rootName) {
    return this.rootReferences.contains(new String(rootName));
  }

  private static final char[][][] EmptyQualifiedNames = new char[0][][];
  private static final char[][] EmptySimpleNames = CharOperation.NO_CHAR_CHAR;

  private static final char[][][] WellKnownQualifiedNames = new char[][][] {TypeConstants.JAVA_LANG_RUNTIMEEXCEPTION, //
      TypeConstants.JAVA_LANG_THROWABLE, //
      TypeConstants.JAVA_LANG_OBJECT, //
      TypeConstants.JAVA_LANG, //
      new char[][] {TypeConstants.JAVA}, //
      new char[][] {new char[] {'o', 'r', 'g'}}, //
      new char[][] {new char[] {'c', 'o', 'm'}}, //
      CharOperation.NO_CHAR_CHAR // default package
  };
  private static final char[][] WellKnownSimpleNames = new char[][] {TypeConstants.JAVA_LANG_RUNTIMEEXCEPTION[2], //
      TypeConstants.JAVA_LANG_THROWABLE[2], //
      TypeConstants.JAVA_LANG_OBJECT[2], //
      TypeConstants.JAVA, //
      TypeConstants.LANG, //
      new char[] {'o', 'r', 'g'}, //
      new char[] {'c', 'o', 'm'} //
  };

  // each array contains qualified char[][], one for size 2, 3, 4, 5, 6, 7 & the rest
  private static final int MaxQualifiedNames = 7;
  private static QualifiedNameSet[] InternedQualifiedNames = new QualifiedNameSet[MaxQualifiedNames];

  // each array contains simple char[], one for size 1 to 29 & the rest
  static final int MaxSimpleNames = 30;
  static NameSet[] InternedSimpleNames = new NameSet[MaxSimpleNames];
  static {
    for (int i = 0; i < MaxQualifiedNames; i++)
      InternedQualifiedNames[i] = new QualifiedNameSet(37);
    for (int i = 0; i < MaxSimpleNames; i++)
      InternedSimpleNames[i] = new NameSet(37);
  }

}
