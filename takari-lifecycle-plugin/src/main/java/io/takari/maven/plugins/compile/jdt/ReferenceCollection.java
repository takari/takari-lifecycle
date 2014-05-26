/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others. All rights reserved. This program and the
 * accompanying materials are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation Tim Hanson <thanson@bea.com> - fix
 * for https://bugs.eclipse.org/bugs/show_bug.cgi?id=137634
 *******************************************************************************/
package io.takari.maven.plugins.compile.jdt;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jdt.core.compiler.CharOperation;


// adopted from org.eclipse.jdt.internal.core.builder.ReferenceCollection
public class ReferenceCollection implements Serializable {

  // contains no simple names as in just 'a' which is kept in simpleNameReferences instead
  Set<String> qualifiedNameReferences;
  Set<String> simpleNameReferences;
  Set<String> rootReferences;

  protected ReferenceCollection(char[][] rootReferences, char[][][] qualifiedNameReferences, char[][] simpleNameReferences) {
    this.qualifiedNameReferences = toStringSet(qualifiedNameReferences);
    this.simpleNameReferences = toStringSet(simpleNameReferences);
    this.rootReferences = toStringSet(rootReferences);
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


  // private void writeObject(java.io.ObjectOutputStream s) {}
  //
  // private void readObject(java.io.ObjectInputStream s) throws java.io.IOException,
  // ClassNotFoundException {}
}
