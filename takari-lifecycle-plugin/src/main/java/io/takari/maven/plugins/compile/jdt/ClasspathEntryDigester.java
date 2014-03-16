package io.takari.maven.plugins.compile.jdt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.codehaus.plexus.util.Base64;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.Multimap;

public class ClasspathEntryDigester {

  private final ClassfileDigester digester = new ClassfileDigester();

  public ClasspathEntryDigester() {}


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


  // index file format
  // one record per line, lines are terminated with '\n'
  // each record is a sequence of values, values are separate by ' ' (space)
  // first value is record type
  // 'T' record: type hash+, where hash is Base64 encoded class file digest

  public static Map<String, byte[]> parseTypeIndex(String index) throws IOException {
    if (index == null) {
      return null;
    }
    return parseTypeIndex(new StringReader(index));
  }

  private static Map<String, byte[]> parseTypeIndex(Reader reader) throws IOException {
    BufferedReader r = new BufferedReader(reader);
    Map<String, byte[]> result = new HashMap<String, byte[]>();
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
      if (st.hasMoreTokens()) {
        result.put(type, Base64.decodeBase64(st.nextToken().getBytes(Charsets.UTF_8)));
      }
    }
    return result;
  }

  public static String toString(Map<String, byte[]> index) {
    StringWriter w = new StringWriter();
    try {
      writeTypeIndex(w, index);
    } catch (IOException e) {
      throw Throwables.propagate(e); // can't really happen
    }
    return w.toString();
  }

  private static void writeTypeIndex(Writer os, Map<String, byte[]> index) throws IOException {
    for (Map.Entry<String, byte[]> entry : index.entrySet()) {
      os.write("T");
      os.write(' ');
      os.write(entry.getKey());
      os.write(' ');
      os.write(new String(Base64.encodeBase64(entry.getValue(), false), Charsets.US_ASCII));
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
