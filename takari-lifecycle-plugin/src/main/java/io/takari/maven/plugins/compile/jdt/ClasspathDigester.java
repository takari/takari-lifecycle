/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInputMetadata;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

@Named
@MojoExecutionScoped
public class ClasspathDigester {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final Map<File, Map<String, byte[]>> CACHE = new ConcurrentHashMap<File, Map<String, byte[]>>();

  private final DefaultBuildContext<?> context;

  private final ClassfileDigester digester;

  @Inject
  public ClasspathDigester(DefaultBuildContext<?> context, MavenProject project, MavenSession session, ClassfileDigester digester) {
    this.context = context;
    this.digester = digester;

    // this is only needed for unit tests, but won't hurt in general
    CACHE.remove(new File(project.getBuild().getOutputDirectory()));
    CACHE.remove(new File(project.getBuild().getTestOutputDirectory()));
  }

  public HashMap<String, byte[]> digestDependencies(List<File> dependencies) throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();

    HashMap<String, byte[]> digest = new HashMap<String, byte[]>();

    // scan dependencies backwards to properly deal with duplicate type definitions
    for (int i = dependencies.size() - 1; i >= 0; i--) {
      File file = dependencies.get(i);

      DefaultInputMetadata<ArtifactFile> metadata = context.registerInput(new ArtifactFileHolder(file));

      if (file.isFile()) {
        digest.putAll(digestJar(metadata));
      } else if (file.isDirectory()) {
        digest.putAll(digestDirectory(metadata));
      } else {
        // happens with reactor dependencies with empty source folders
        continue;
      }
    }

    log.debug("Analyzed {} classpath dependencies ({} ms)", dependencies.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));

    return digest;
  }

  private Map<String, byte[]> digestJar(InputMetadata<ArtifactFile> metadata) throws IOException {
    final File file = metadata.getResource().file;
    Map<String, byte[]> digest = CACHE.get(file);
    if (digest == null) {
      digest = new HashMap<String, byte[]>();
      JarFile jar = new JarFile(file);
      try {
        for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
          JarEntry entry = entries.nextElement();
          String path = entry.getName();
          if (path.endsWith(".class")) {
            String type = toJavaType(path);
            try {
              digest.put(type, digester.digest(ClassFileReader.read(jar, path)));
            } catch (ClassFormatException e) {
              // the class file is old for sure, according to jdt
            }
          }
        }
      } finally {
        jar.close();
      }
      CACHE.put(file, digest);
    }

    return digest;
  }

  private Map<String, byte[]> digestDirectory(InputMetadata<ArtifactFile> metadata) throws IOException {
    final File directory = metadata.getResource().file;
    Map<String, byte[]> digest = CACHE.get(directory);
    if (digest == null) {
      digest = new HashMap<String, byte[]>();
      DirectoryScanner scanner = new DirectoryScanner();
      scanner.setBasedir(directory);
      scanner.setIncludes(new String[] {"**/*.class"});
      scanner.scan();
      for (String path : scanner.getIncludedFiles()) {
        String type = toJavaType(path);
        try {
          digest.put(type, digester.digest(ClassFileReader.read(new File(directory, path))));
        } catch (ClassFormatException e) {
          // as far as jdt is concerned, the type does not exist
        }
      }
      CACHE.put(directory, digest);
    }

    return digest;
  }

  public static String toJavaType(String path) {
    path = path.substring(0, path.length() - ".class".length());
    return path.replace('/', '.').replace('\\', '.');
  }
}
