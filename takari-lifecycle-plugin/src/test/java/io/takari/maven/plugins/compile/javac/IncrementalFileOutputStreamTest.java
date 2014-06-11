package io.takari.maven.plugins.compile.javac;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.io.Files;

public class IncrementalFileOutputStreamTest {

  private final Random rnd = new Random(12345);

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  protected void writeAndAssertBuff(File file, byte[] data) throws IOException {
    try (IncrementalFileOutputStream os = new IncrementalFileOutputStream(file)) {
      os.write(data);
    }
    Assert.assertArrayEquals(data, Files.asByteSource(file).read());
  }

  protected void writeAndAssert(File file, byte[] data) throws IOException {
    try (IncrementalFileOutputStream os = new IncrementalFileOutputStream(file)) {
      for (int i = 0; i < data.length; i++) {
        os.write(data[i]);
      }
    }
    Assert.assertArrayEquals(data, Files.asByteSource(file).read());
  }

  protected byte[] data(int length) {
    byte[] result = new byte[length];
    rnd.nextBytes(result);
    return result;
  }

  protected byte[] resize(byte[] data, int lengthDelta) {
    byte[] result = data(data.length + lengthDelta);
    System.arraycopy(data, 0, result, 0, Math.min(data.length, result.length));
    return result;
  }

  protected void change(byte[] data, int offset) {
    data[offset] ^= 0xff;
  }

  //
  // single-byte write(byte) tests
  //

  @Test
  public void testSame() throws Exception {
    File file = temp.newFile();
    byte[] data = data(10);
    Files.write(data, file);
    writeAndAssert(file, data);
  }

  @Test
  public void testExtend() throws Exception {
    File file = temp.newFile();
    byte[] data = data(10);
    Files.write(data, file);
    data = resize(data, +10);
    writeAndAssert(file, data);
  }

  @Test
  public void testTruncate() throws Exception {
    File file = temp.newFile();
    byte[] data = data(20);
    Files.write(data, file);
    data = resize(data, -10);
    writeAndAssert(file, data);
  }

  //
  // multi-byte write(byte[]) tests
  //

  @Test
  public void testBuff_same_under_BUF_SIZE() throws Exception {
    File file = temp.newFile();
    byte[] data = data(IncrementalFileOutputStream.BUF_SIZE / 2);
    Files.write(data, file);
    writeAndAssertBuff(file, data);
  }

  @Test
  public void testBuff_same_BUF_SIZE() throws Exception {
    File file = temp.newFile();
    byte[] data = data(IncrementalFileOutputStream.BUF_SIZE);
    Files.write(data, file);
    writeAndAssertBuff(file, data);
  }

  @Test
  public void testBuff_same_over_BUF_SIZE() throws Exception {
    File file = temp.newFile();
    byte[] data = data(IncrementalFileOutputStream.BUF_SIZE + 10);
    Files.write(data, file);
    writeAndAssertBuff(file, data);
  }

  @Test
  public void testBuff_diff_under_BUF_SIZE() throws Exception {
    File file = temp.newFile();
    byte[] data = data(IncrementalFileOutputStream.BUF_SIZE / 2);
    Files.write(data, file);
    change(data, IncrementalFileOutputStream.BUF_SIZE / 2 - 10);
    writeAndAssertBuff(file, data);
  }

  @Test
  public void testBuff_diff_offset_0() throws Exception {
    File file = temp.newFile();
    byte[] data = data(IncrementalFileOutputStream.BUF_SIZE / 2);
    Files.write(data, file);
    change(data, 0);
    writeAndAssertBuff(file, data);
  }


  @Test
  public void testBuff_diff_over_BUF_SIZE() throws Exception {
    File file = temp.newFile();
    byte[] data = data(IncrementalFileOutputStream.BUF_SIZE + 10);
    Files.write(data, file);
    change(data, IncrementalFileOutputStream.BUF_SIZE + 2);
    writeAndAssertBuff(file, data);
  }

  @Test
  public void testBuff_truncate() throws Exception {
    File file = temp.newFile();
    byte[] data = data(IncrementalFileOutputStream.BUF_SIZE + 10);
    Files.write(data, file);
    data = resize(data, -10);
    writeAndAssertBuff(file, data);
  }

  @Test
  public void testBuff_extend() throws Exception {
    File file = temp.newFile();
    byte[] data = data(IncrementalFileOutputStream.BUF_SIZE - 10);
    Files.write(data, file);
    data = resize(data, +10);
    writeAndAssertBuff(file, data);
  }
}
