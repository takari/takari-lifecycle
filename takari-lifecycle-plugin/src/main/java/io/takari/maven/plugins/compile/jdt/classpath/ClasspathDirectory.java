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

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.AccessRestriction;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public class ClasspathDirectory extends AbstractClasspathDirectory implements ClasspathEntry {

  private ClasspathDirectory(File directory) {
    super(directory);
  }

  @Override
  protected NameEnvironmentAnswer findType0(String packageName, String typeName, AccessRestriction accessRestriction) throws IOException, ClassFormatException {
    File classFile = getFile(packageName, typeName, SUFFIX_STRING_class);
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
    return new ClasspathDirectory(directory);
  }

}
