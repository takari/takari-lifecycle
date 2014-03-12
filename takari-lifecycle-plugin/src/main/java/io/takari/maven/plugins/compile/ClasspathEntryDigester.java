package io.takari.maven.plugins.compile;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.util.Base64;
import org.codehaus.plexus.util.DirectoryScanner;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.*;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

@Named
@Singleton
public class ClasspathEntryDigester {

  private final Logger log = LoggerFactory.getLogger(getClass());

  public static final String TYPE_INDEX_LOCATION = "META-INF/incrementalbuild/types.index";

  private final ClassfileDigester digester = new ClassfileDigester();

  public ClasspathEntryDigester() {}

  public void writeIndex(File outputDirectory, Multimap<String, byte[]> index) throws IOException {
    File indexFile = new File(outputDirectory, TYPE_INDEX_LOCATION);
    indexFile.getParentFile().mkdirs();
    OutputStreamWriter writer =
        new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(indexFile)),
            Charsets.UTF_8);
    try {
      writeTypeIndex(writer, index);
    } finally {
      writer.close();
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
    Stopwatch stopwatch = new Stopwatch().start();
    Multimap<String, byte[]> index;
    boolean persisted;
    ZipFile zip = new ZipFile(file);
    try {
      ZipEntry indexEntry = zip.getEntry(TYPE_INDEX_LOCATION);
      if (indexEntry != null) {
        // modern zip, use provided type index
        InputStream is = zip.getInputStream(indexEntry);
        try {
          persisted = true;
          index = parseTypeIndex(is);
        } finally {
          is.close();
        }
      } else {
        // this is a legacy zip that does not provide type index
        // use the zip simple hash for all zip entries for now
        // if zip changes, all entries will be assumed changed
        persisted = false;
        index = ArrayListMultimap.create();
        byte[] hash = getSimpleHash(file);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          String fileName = entry.getName();
          if (entry.getName().endsWith(".class")) {
            index.put(pathToType(fileName), hash);
          }
        }
      }
    } finally {
      zip.close();
    }
    log.info("zip {} persistent {} size {} {} ms", file, persisted, index.size(),
        stopwatch.elapsed(TimeUnit.MILLISECONDS));
    return new ClasspathEntryIndex(index, persisted);
  }

  private byte[] getSimpleHash(File file) {
    byte[] hash = new byte[16];
    toByteArray(hash, 0, file.length());
    toByteArray(hash, 8, file.lastModified());
    return hash;
  }

  private void toByteArray(byte[] bytes, int offset, long value) {
    bytes[offset + 0] = (byte) ((value >> 0x00) & 0xFF);
    bytes[offset + 1] = (byte) ((value >> 0x08) & 0xFF);
    bytes[offset + 2] = (byte) ((value >> 0x10) & 0xFF);
    bytes[offset + 3] = (byte) ((value >> 0x18) & 0xFF);
    bytes[offset + 4] = (byte) ((value >> 0x20) & 0xFF);
    bytes[offset + 5] = (byte) ((value >> 0x28) & 0xFF);
    bytes[offset + 6] = (byte) ((value >> 0x30) & 0xFF);
    bytes[offset + 7] = (byte) ((value >> 0x38) & 0xFF);
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

    Stopwatch stopwatch = new Stopwatch().start();

    boolean persisted = true;
    Multimap<String, byte[]> index = null;;

    final File indexFile = new File(dir, TYPE_INDEX_LOCATION);
    if (indexFile.isFile()) {
      InputStream is = new FileInputStream(indexFile);
      try {
        index = parseTypeIndex(is);
      } finally {
        is.close();
      }
    }

    if (index == null || indexFile.lastModified() < timestamp) {
      // index file does not exist or was created before the build started
      // assume the index is stale and merge/rebuild

      persisted = false;

      Multimap<String, byte[]> persistedIndex = index;
      index = ArrayListMultimap.create();

      timestamp = indexFile.lastModified(); // returns 0 if index file does not exist

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
          // use existing hash(es)
          hashes = persistedIndex.get(type);
        }
        if (hashes == null) {
          hashes = Collections.singleton(getSimpleHash(file));
        }
        index.putAll(type, hashes);
      }
    }
    log.info("dir {} persistent {} size {} {} ms", dir, persisted, index.size(),
        stopwatch.elapsed(TimeUnit.MILLISECONDS));
    return new ClasspathEntryIndex(index, persisted);
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

  private static Multimap<String, byte[]> parseTypeIndex(InputStream is) throws IOException {
    return parseTypeIndex(new InputStreamReader(is, Charsets.UTF_8));
  }

  public static Multimap<String, byte[]> parseTypeIndex(String index) throws IOException {
    if (index == null) {
      return null;
    }
    return parseTypeIndex(new StringReader(index));
  }

  private static Multimap<String, byte[]> parseTypeIndex(Reader reader) throws IOException {
    BufferedReader r = new BufferedReader(reader);
    Multimap<String, byte[]> result = ArrayListMultimap.create();
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
    return result;
  }

  public static String toString(Multimap<String, byte[]> index) {
    StringWriter w = new StringWriter();
    try {
      writeTypeIndex(w, index);
    } catch (IOException e) {
      throw Throwables.propagate(e); // can't really happen
    }
    return w.toString();
  }

  private static void writeTypeIndex(Writer os, Multimap<String, byte[]> index) throws IOException {
    for (Map.Entry<String, Collection<byte[]>> entry : index.asMap().entrySet()) {
      os.write("T");
      os.write(' ');
      os.write(entry.getKey());
      for (byte[] hash : entry.getValue()) {
        os.write(' ');
        os.write(new String(Base64.encodeBase64(hash, false), Charsets.US_ASCII));
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
