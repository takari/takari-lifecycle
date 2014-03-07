package io.takari.maven.plugins.compiler.incremental;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.util.Base64;
import org.codehaus.plexus.util.DirectoryScanner;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

@Named
@Singleton
public class ClasspathEntryDigester {

  public static final String TYPE_INDEX_LOCATION = "META-INF/incrementalbuild/types.index";

  private final ClassfileDigester digester = new ClassfileDigester();

  public ClasspathEntryDigester() {}

  public void writeIndex(File outputDirectory, Multimap<String, byte[]> index) throws IOException {
    File indexFile = new File(outputDirectory, TYPE_INDEX_LOCATION);
    indexFile.getParentFile().mkdirs();
    OutputStream os = new FileOutputStream(indexFile);
    try {
      writeTypeIndex(os, index);
    } finally {
      os.close();
    }
  }

  public ClasspathEntryIndex readIndex(File resource, long timestamp) throws IOException {
    if (resource.isFile()) {
      return getJarTypeDigest(resource);
    } else if (resource.isDirectory()) {
      return getDirectorTypeDigest(resource, timestamp);
    }
    throw new IllegalArgumentException("File does not exist " + resource);
  }

  private ClasspathEntryIndex getJarTypeDigest(File file) throws IOException {
    ZipFile zip = new ZipFile(file);
    try {
      ZipEntry index = zip.getEntry(TYPE_INDEX_LOCATION);
      if (index != null) {
        InputStream is = zip.getInputStream(index);
        try {
          return readTypeIndex(is);
        } finally {
          is.close();
        }
      }
      Multimap<String, byte[]> result = ArrayListMultimap.create();
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String fileName = entry.getName();
        if (entry.getName().endsWith(".class")) {
          InputStream is = zip.getInputStream(entry);
          try {
            byte[] hash = digest(is, fileName);
            if (hash != null) {
              result.put(pathToType(fileName), hash);
            }
          } finally {
            is.close();
          }
        }
      }
      return new ClasspathEntryIndex(result, false);
    } finally {
      zip.close();
    }
  }

  private byte[] digest(InputStream inputStream, String fileName) throws IOException {
    try {
      return digestClass(ClassFileReader.read(inputStream, fileName));
    } catch (ClassFormatException e) {
      // as far as jdt compiler is concerned, the class is not present
    }
    return null;
  }

  public byte[] digestClass(ClassFileReader reader) {
    if (!reader.isLocal() && !reader.isAnonymous()) {
      // note on using smaller number of bytes per hash
      // for ~20k classes in the index
      // using full 20 byte hash results in index size ~1.6M txt ~762k zip
      // using 10 byte hash results in index size ~1.3M txt ~535k zip
      // zip of the ~20k classes is ~52M, so index size is insignificant (1.5%)
      // reduced hash size does not noticeably reduce index size
      return digester.digest(reader);
    }
    return null;
  }

  private ClasspathEntryIndex getDirectorTypeDigest(File dir, long timestamp) throws IOException {
    // on fairly slow 13-inch, Mid 2012 macbook air (yes, I did say "macbook air")
    // indexing ~23k class files takes ~9.5 seconds with empty filesystem cache
    // indexing the same ~23k class files takes ~3.5 seconds with warm filesystem cache
    // checking timestapt&size of the same 23k class takes ~1second

    ClasspathEntryIndex persistedIndex = null;;

    final File indexFile = new File(dir, TYPE_INDEX_LOCATION);
    if (indexFile.isFile()) {
      InputStream is = new FileInputStream(indexFile);
      try {
        persistedIndex = readTypeIndex(is);
      } finally {
        is.close();
      }
    }

    if (persistedIndex != null && indexFile.lastModified() >= timestamp) {
      // index file exists and was created after the build started
      // assume the index is up-to-date
      return persistedIndex;
    }

    timestamp = indexFile.lastModified(); // returns 0 if index file does not exist

    Multimap<String, byte[]> result = ArrayListMultimap.create();
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(dir);
    scanner.setIncludes(new String[] {"**/*.class"});
    scanner.scan();
    for (String rpath : scanner.getIncludedFiles()) {
      String type = pathToType(rpath);
      Collection<byte[]> hashes = null;
      File file = new File(dir, rpath);
      if (persistedIndex != null && file.lastModified() <= timestamp) {
        // the file was last modified before persisted index was created
        // use existing hash(es) if available
        hashes = persistedIndex.getIndex().get(type);
      }
      if (hashes == null) {
        InputStream is = new FileInputStream(file);
        try {
          byte[] hash = digest(is, rpath);
          if (hash != null) {
            hashes = Collections.singleton(hash);
          }
        } finally {
          is.close();
        }
      }
      if (hashes != null) {
        result.putAll(type, hashes);
      }
    }
    return new ClasspathEntryIndex(result, false);
  }

  private String pathToType(String rpath) {
    if (rpath.endsWith(".class")) {
      rpath = rpath.substring(0, rpath.length() - ".class".length());
    }
    return rpath.replace('/', '.').replace('\\', '.');
  }

  // index file format
  // one record per line, lines are terminated with '\n'
  // each record is a sequence of values, values are separate by ' ' (space)
  // first value is record type
  // 'T' record: type hash+, where hash is Base64 encoded class file digest

  private ClasspathEntryIndex readTypeIndex(InputStream is) throws IOException {
    Multimap<String, byte[]> result = ArrayListMultimap.create();
    BufferedReader r = new BufferedReader(new InputStreamReader(is, Charsets.UTF_8));
    String str;
    while ((str = r.readLine()) != null) {
      final StringTokenizer st = new StringTokenizer(str, " ");
      if (!st.hasMoreTokens() || !"T".equals(st.nextToken())) {
        throw new IllegalArgumentException("Corrupted type index");
      }
      if (!st.hasMoreTokens()) {
        throw new IllegalArgumentException("Corrupted type index");
      }
      final String type = st.nextToken();
      while (st.hasMoreTokens()) {
        result.put(type, Base64.decodeBase64(st.nextToken().getBytes(Charsets.UTF_8)));
      }
    }
    return new ClasspathEntryIndex(result, true);
  }

  private void writeTypeIndex(OutputStream os, Multimap<String, byte[]> index) throws IOException {
    for (Map.Entry<String, Collection<byte[]>> entry : index.asMap().entrySet()) {
      os.write("T".getBytes(Charsets.UTF_8));
      os.write(' ');
      os.write(entry.getKey().getBytes(Charsets.UTF_8));
      for (byte[] hash : entry.getValue()) {
        os.write(' ');
        os.write(Base64.encodeBase64(hash, false));
      }
      os.write('\n');
    }
  }

  public static Set<String> diff(Multimap<String, byte[]> left, Multimap<String, byte[]> right) {
    if (left == null) {
      if (right != null) {
        return right.keySet();
      }
      return Collections.emptySet();
    }
    if (right == null) {
      return Collections.emptySet();
    }
    Set<String> result = new HashSet<String>();
    for (Map.Entry<String, Collection<byte[]>> entry : left.asMap().entrySet()) {
      String type = entry.getKey();
      if (!equals(entry.getValue(), right.get(type))) {
        result.add(type);
      }
    }
    for (Map.Entry<String, Collection<byte[]>> entry : right.asMap().entrySet()) {
      String type = entry.getKey();
      if (!left.containsKey(type)) {
        result.add(type);
      }
    }
    return result;
  }

  private static boolean equals(Collection<byte[]> a, Collection<byte[]> b) {
    if (a == null) {
      return b == null;
    }
    if (b == null) {
      return false;
    }
    if (a.size() != b.size()) {
      return false;
    }
    // NB classpath order is important
    Iterator<byte[]> ai = a.iterator();
    Iterator<byte[]> bi = b.iterator();
    while (ai.hasNext()) {
      if (!Arrays.equals(ai.next(), bi.next())) {
        return false;
      }
    }
    return true;
  }

}
