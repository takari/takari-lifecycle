/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.testing.executor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface MavenInstallations {

  /*
   * Note on installation names. In most cases it will be desirable to give custom distributions a meaningful name and use that name as part of test name. Most likely this will be useful not only for
   * integration tests but in other scenarios too (Hudson and m2e immediately come to mind), so the name/version need to be embedded in the maven installation itself. For example, it can be a file
   * under ${maven.home}/conf directory, similar to /etc/issue used by linux distributions. It is also possible to include distribution name/version in one of the jars, similar to how maven version is
   * already included in META-INF/maven/org.apache.maven/maven-core/pom.properties.
   */
  public String[] value();

}
