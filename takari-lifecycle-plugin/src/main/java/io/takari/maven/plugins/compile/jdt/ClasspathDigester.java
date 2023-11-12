/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import static org.eclipse.jdt.internal.compiler.util.SuffixConstants.SUFFIX_STRING_class;
import static org.eclipse.jdt.internal.compiler.util.SuffixConstants.SUFFIX_STRING_java;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

@Named
@MojoExecutionScoped
public class ClasspathDigester {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final Map<File, Map<String, byte[]>> CACHE = new ConcurrentHashMap<>();

  private final ClassfileDigester digester;

  @Inject
  public ClasspathDigester(MavenProject project, MavenSession session, ClassfileDigester digester) {
    this.digester = digester;

    // this is only needed for unit tests, but won't hurt in general
    CACHE.remove(new File(project.getBuild().getOutputDirectory()));
    CACHE.remove(new File(project.getBuild().getTestOutputDirectory()));
  }

  public HashMap<String, byte[]> digestDependencies(List<File> dependencies) throws IOException {
    long started = System.currentTimeMillis();

    HashMap<String, byte[]> digest = new HashMap<>();

    // scan dependencies backwards to properly deal with duplicate type definitions
    for (int i = dependencies.size() - 1; i >= 0; i--) {
      File file = dependencies.get(i);
      if (file.isFile()) {
        digest.putAll(digestJar(file));
      } else if (file.isDirectory()) {
        digest.putAll(digestDirectory(file));
      } else {
        // happens with reactor dependencies with empty source folders
        continue;
      }
    }

    log.debug("Analyzed {} classpath dependencies ({} ms)", dependencies.size(), System.currentTimeMillis() - started);

    return digest;
  }

  private Map<String, byte[]> digestJar(final File file) throws IOException {
    Map<String, byte[]> digest = CACHE.get(file);
    if (digest == null) {
      digest = new HashMap<>();
      Map<String, byte[]> sourcesDigest = new HashMap<>();
      try (JarFile jar = new JarFile(file)) {
        for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
          JarEntry entry = entries.nextElement();
          String path = entry.getName();
          if (path.endsWith(SUFFIX_STRING_class)) {
            String type = toJavaType(path, SUFFIX_STRING_class);
            try {
              digest.put(type, digester.digest(ClassFileReader.read(jar, path)));
            } catch (ClassFormatException e) {
              // as far as jdt is concerned, the type does not exist
            }
          } else if (path.endsWith(SUFFIX_STRING_java)) {
            String type = toJavaType(path, SUFFIX_STRING_java);
            try (InputStream in = jar.getInputStream(entry)) {
              sourcesDigest.put(type, sha1bytes(in));
            }
          }
        }
      }
      mergeAll(digest, sourcesDigest);
      CACHE.put(file, digest);
    }

    return digest;
  }

  private Map<String, byte[]> digestDirectory(final File directory) throws IOException {
    Map<String, byte[]> digest = CACHE.get(directory);
    if (digest == null) {
      digest = new HashMap<>();
      Map<String, byte[]> sourcesDigest = new HashMap<>();
      DirectoryScanner scanner = new DirectoryScanner();
      scanner.setBasedir(directory);
      scanner.setIncludes(new String[] {"**/*" + SUFFIX_STRING_class, "**/*" + SUFFIX_STRING_java});
      scanner.scan();
      for (String path : scanner.getIncludedFiles()) {
        if (path.endsWith(SUFFIX_STRING_class)) {
          String type = toJavaType(path, SUFFIX_STRING_class);
          try {
            digest.put(type, digester.digest(ClassFileReader.read(new File(directory, path))));
          } catch (ClassFormatException e) {
            // as far as jdt is concerned, the type does not exist
          }
        } else {
          String type = toJavaType(path, SUFFIX_STRING_java);
          try (InputStream inputStream = Files.newInputStream(directory.toPath().resolve(path))) {
            sourcesDigest.put(type, sha1bytes(inputStream));
          }
        }
      }
      mergeAll(digest, sourcesDigest);
      CACHE.put(directory, digest);
    }

    return digest;
  }

  private void mergeAll(Map<String, byte[]> target, Map<String, byte[]> source) {
    for (Map.Entry<String, byte[]> entry : source.entrySet()) {
      byte[] value = target.get(entry.getKey());
      if (value != null) {
        byte[] temp = new byte[value.length + entry.getValue().length];
        System.arraycopy(value, 0, temp, 0, value.length);
        System.arraycopy(entry.getValue(), 0, temp, value.length, entry.getValue().length);
        value = temp;
      } else {
        value = entry.getValue();
      }
      target.put(entry.getKey(), value);
    }
  }

  public static String toJavaType(String path, String suffix) {
    path = path.substring(0, path.length() - suffix.length());
    return path.replace('/', '.').replace('\\', '.');
  }

  public static void flush() {
    CACHE.clear();
  }

  private static byte[] sha1bytes(final InputStream inputStream) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] buffer = new byte[8192];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        md.update(buffer, 0, read);
      }
      return md.digest();
    } catch ( NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unsupported JVM: sha1 MessageDigest algorithm unsupported", e);
    }
  }

}
