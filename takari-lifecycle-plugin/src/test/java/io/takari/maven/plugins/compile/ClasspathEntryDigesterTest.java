package io.takari.maven.plugins.compile;

import static org.apache.maven.plugin.testing.resources.TestResources.cp;
import io.takari.maven.plugins.compiler.incremental.ClasspathEntryDigester;
import io.takari.maven.plugins.compiler.incremental.ClasspathEntryIndex;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import org.apache.maven.plugin.testing.resources.TestResources;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class ClasspathEntryDigesterTest {

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final CompileRule mojos = new CompileRule();

  private ClasspathEntryDigester digester = new ClasspathEntryDigester();

  private final File basedir = new File("src/test/projects/classpath-digester");

  private final long now = System.currentTimeMillis() - 10000;

  @Test
  public void testReadJarIndex() throws Exception {
    ClasspathEntryIndex index =
        digester.readIndex(new File(basedir, "jar/jar-with-index.jar"), now);
    Assert.assertTrue(index.isPersistent());
  }

  @Test
  public void testDigestJarWithoutIndex() throws Exception {
    ClasspathEntryIndex index =
        digester.readIndex(new File(basedir, "jar/jar-without-index.jar"), now);
    Assert.assertFalse(index.isPersistent());
  }

  @Test
  public void testClassFolderIndex() throws Exception {
    File basedir = resources.getBasedir("classpath-digester/folder");
    File classes = new File(basedir, "target/classes/");
    mojos.executeMojo(basedir, "compile-incremental");
    cp(basedir, "pom.xml", "target/classes/CorruptedClass.class");

    ClasspathEntryIndex index = digester.readIndex(classes, now);
    Assert.assertTrue(index.isPersistent());
    Assert.assertEquals(3, index.getIndex().size());

    Assert.assertTrue(new File(classes, ClasspathEntryDigester.TYPE_INDEX_LOCATION).delete());
    index = digester.readIndex(new File(basedir, "target/classes"), now);
    Assert.assertFalse(index.isPersistent());
  }

  @Test
  public void testClassFolderIndex_uptodateIndex() throws Exception {
    // the point of this test is to verify that class folders are not reindexed
    // if type index was created after build has started

    File basedir = resources.getBasedir("classpath-digester/folder");
    File classes = new File(basedir, "target/classes/");
    mojos.executeMojo(basedir, "compile-incremental");

    byte[] knownHash = "test".getBytes("UTF-8");
    {
      Multimap<String, byte[]> index = ArrayListMultimap.create();
      index.put("folder.OuterClass", knownHash);
      index.put("folder.RemovedClass", knownHash);
      digester.writeIndex(classes, index);
    }

    ClasspathEntryIndex index = digester.readIndex(classes, now);
    Assert.assertTrue(index.isPersistent());
    Assert.assertEquals(2, index.getIndex().size());
    Assert.assertArrayEquals(knownHash, getHash(index, "folder.OuterClass"));
    Assert.assertArrayEquals(knownHash, getHash(index, "folder.RemovedClass"));
  }

  private byte[] getHash(ClasspathEntryIndex index, String type) {
    Collection<byte[]> hashes = index.getIndex().get(type);
    Assert.assertEquals(1, hashes.size());
    return hashes.iterator().next();
  }

  @Test
  public void testClassFolderIndex_reindex() throws Exception {
    // the point of the test is to verify that classes that are older than the index
    // are NOT reindexed. classes newer than the index are reindexed

    File basedir = resources.getBasedir("classpath-digester/folder");
    File classes = new File(basedir, "target/classes/");
    File indexFile = new File(classes, ClasspathEntryDigester.TYPE_INDEX_LOCATION);
    mojos.executeMojo(basedir, "compile-incremental");

    byte[] knownHash = "test".getBytes("UTF-8");
    {
      Multimap<String, byte[]> index = ArrayListMultimap.create();
      index.put("folder.FolderClassA", knownHash);
      index.put("folder.FolderClassB", knownHash);
      index.put("folder.OuterClass", knownHash);
      index.put("folder.RemovedClass", knownHash);
      digester.writeIndex(classes, index);
    }

    final long two_seconds = 2000;
    final long ten_seconds = 10000;
    indexFile.setLastModified(now - two_seconds);
    new File(classes, "folder/OuterClass.class").setLastModified(now - ten_seconds);
    new File(classes, "folder/FolderClassA.class").setLastModified(now);
    new File(classes, "folder/FolderClassB.class").setLastModified(now);

    ClasspathEntryIndex index = digester.readIndex(classes, now);
    Assert.assertFalse(index.isPersistent());
    Assert.assertEquals(3, index.getIndex().size());
    Assert.assertTrue(Arrays.equals(knownHash, getHash(index, "folder.OuterClass")));
    Assert.assertFalse(Arrays.equals(knownHash, getHash(index, "folder.FolderClassA")));
    Assert.assertFalse(Arrays.equals(knownHash, getHash(index, "folder.FolderClassB")));
  }

}
