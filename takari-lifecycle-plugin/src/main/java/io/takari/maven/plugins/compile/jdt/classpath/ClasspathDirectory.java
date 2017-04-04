/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import static org.eclipse.jdt.internal.compiler.util.SuffixConstants.SUFFIX_STRING_class;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public class ClasspathDirectory extends AbstractClasspathDirectory implements ClasspathEntry {

  private ClasspathDirectory(File directory, Set<String> packages, Map<String, File> files) {
    super(directory, packages, files);
  }

  @Override
  protected NameEnvironmentAnswer findType0(String packageName, String typeName, AccessRestriction accessRestriction) throws IOException, ClassFormatException {
    File classFile = getFile(packageName, typeName);
    if (classFile != null) {
      return new NameEnvironmentAnswer(ClassFileReader.read(classFile, false), accessRestriction);
    }
    return null;
  }

  @Override
  public String toString() {
    return "Classpath for directory " + file;
  }

  public static ClasspathDirectory create(File directory) {
    Set<String> packages = new HashSet<>();
    Map<String, File> files = new HashMap<>();
    scanDirectory(directory, SUFFIX_STRING_class, packages, files);
    return new ClasspathDirectory(directory, packages, files);
  }

}
