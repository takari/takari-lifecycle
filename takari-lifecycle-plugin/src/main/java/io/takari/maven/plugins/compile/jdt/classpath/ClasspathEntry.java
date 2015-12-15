/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt.classpath;

import java.util.Collection;

import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

public interface ClasspathEntry {

  Collection<String> getPackageNames();

  NameEnvironmentAnswer findType(String packageName, String typeName);

  String getEntryDescription();
}
