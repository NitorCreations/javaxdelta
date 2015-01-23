/*
 * Copyright (c) 2003, 2007 s IT Solutions AT Spardat GmbH.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 *
 */
package at.spardat.xma.xdelta.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Enumeration;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import at.spardat.xma.xdelta.JarDelta;
import at.spardat.xma.xdelta.JarPatcher;

/**
 * This class tests JarDelta and JarPatcher with randomly generated zip files.
 *
 * @author S3460
 */
public class JarDeltaJarPatcherTest {
  /** The random. */
  private SecureRandom random;
  /** The byte max length. */
  private int byteMaxLength = 1000;
  /** The entry max size. */
  private int entryMaxSize = 10;
  /** The source file. */
  private File sourceFile; //das originale File
  /** The target file. */
  private File targetFile; //die neue Generation
  /** The patch file. */
  private File patchFile; //die Unterschiede
  /** The result file. */
  private File resultFile; //das erechnete Result

  /**
   * Instantiates a new jar delta jar patcher test.
   */
  public JarDeltaJarPatcherTest() {
    try {
      random = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      throw new RuntimeException(e.getMessage());
    }
  }

  /**
   * Sets the up.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUp() throws Exception {
    sourceFile = File.createTempFile("JarDeltaJarPatcherTest_Source", ".zip");
    sourceFile.deleteOnExit();
    targetFile = File.createTempFile("JarDeltaJarPatcherTest_Target", ".zip");
    targetFile.deleteOnExit();
    patchFile = File.createTempFile("JarDeltaJarPatcherTest_Patch", ".zip");
    patchFile.deleteOnExit();
    resultFile = File.createTempFile("JarDeltaJarPatcherTest_Result", ".zip");
    resultFile.deleteOnExit();
  }

  /**
   * Tear down.
   *
   * @throws Exception the exception
   */
  @After
  public void tearDown() throws Exception {
    cleanUp();
  }

  /**
   * Clean up.
   *
   * @author S3460
   * @since version_number
   */
  private void cleanUp() {
    if (sourceFile != null) {
      sourceFile.delete();
    }
    if (targetFile != null) {
      targetFile.delete();
    }
    if (patchFile != null) {
      patchFile.delete();
    }
    if (resultFile != null) {
      resultFile.delete();
    }
  }

  /**
   * Creates a zip file with random content.
   *
   * @author S3460
   * @param source the source
   * @return the zip file
   * @throws Exception the exception
   */
  private ZipFile makeSourceZipFile(File source) throws Exception {
    ZipArchiveOutputStream out = new ZipArchiveOutputStream(new FileOutputStream(source));
    int size = randomSize(entryMaxSize);
    for (int i = 0; i < size; i++) {
      out.putArchiveEntry(new ZipArchiveEntry("zipentry" + i));
      int anz = randomSize(10);
      for (int j = 0; j < anz; j++) {
        byte[] bytes = getRandomBytes();
        out.write(bytes, 0, bytes.length);
      }
      out.flush();
      out.closeArchiveEntry();
    }
    //add leeres Entry
    out.putArchiveEntry(new ZipArchiveEntry("zipentry" + size));
    out.flush();
    out.closeArchiveEntry();
    out.flush();
    out.finish();
    out.close();
    return new ZipFile(source);
  }

  /**
   * Writes a modified version of zip_Source into target.
   *
   * @author S3460
   * @param zipSource the zip source
   * @param target the target
   * @return the zip file
   * @throws Exception the exception
   */
  private ZipFile makeTargetZipFile(ZipFile zipSource, File target) throws Exception {
    ZipArchiveOutputStream out = new ZipArchiveOutputStream(new FileOutputStream(target));
    for (Enumeration<ZipArchiveEntry> enumer = zipSource.getEntries(); enumer.hasMoreElements();) {
      ZipArchiveEntry sourceEntry = enumer.nextElement();
      out.putArchiveEntry(new ZipArchiveEntry(sourceEntry.getName()));
      byte[] oldBytes = toBytes(zipSource, sourceEntry);
      byte[] newBytes = getRandomBytes();
      byte[] mixedBytes = mixBytes(oldBytes, newBytes);
      out.write(mixedBytes, 0, mixedBytes.length);
      out.flush();
      out.closeArchiveEntry();
    }
    out.putArchiveEntry(new ZipArchiveEntry("zipentry" + entryMaxSize + 1));
    byte[] bytes = getRandomBytes();
    out.write(bytes, 0, bytes.length);
    out.flush();
    out.closeArchiveEntry();
    out.putArchiveEntry(new ZipArchiveEntry("zipentry" + (entryMaxSize + 2)));
    out.closeArchiveEntry();
    out.flush();
    out.finish();
    out.close();
    return new ZipFile(targetFile);
  }

  /**
   * Copies a modified version of oldBytes into newBytes by mixing some bytes.
   *
   * @param oldBytes the old bytes
   * @param newBytes the new bytes
   * @return the byte[]
   */
  private byte[] mixBytes(byte[] oldBytes, byte[] newBytes) {
    byte[] bytes = new byte[oldBytes.length + newBytes.length];
    if (oldBytes.length == 0) {
      return newBytes;
    }
    if (newBytes.length == 0) {
      return oldBytes;
    }
    System.arraycopy(oldBytes, 0, bytes, 0, oldBytes.length / 2);
    System.arraycopy(newBytes, 0, bytes, oldBytes.length / 2 - 1, newBytes.length / 2);
    System.arraycopy(oldBytes, oldBytes.length / 2, bytes, oldBytes.length / 2 + newBytes.length / 2 - 1, oldBytes.length / 2);
    System.arraycopy(newBytes, newBytes.length / 2, bytes, oldBytes.length + newBytes.length / 2 - 1, newBytes.length / 2);
    //      System.arraycopy(oldBytes,0,bytes,0,oldBytes.length);
    //      System.arraycopy(newBytes,0,bytes,oldBytes.length-1,newBytes.length);
    //        int chunkSize = randomSize(10);
    //        for (int i = chunkSize; i > 0; i--) {
    //            System.arraycopy(oldBytes,oldBytes.length/i,bytes,0,oldBytes.length/chunkSize);
    //            System.arraycopy(newBytes,newBytes.length/i,bytes,oldBytes.length/chunkSize-1,newBytes.length/chunkSize);
    //        }
    return bytes;
  }

  /**
   * Converts the given zip entry to a byte array.
   *
   * @author S3460
   * @param zipfile the zipfile
   * @param entry the entry
   * @return the byte[]
   * @throws Exception the exception
   */
  private byte[] toBytes(ZipFile zipfile, ZipArchiveEntry entry) throws Exception {
    int entrySize = (int) entry.getSize();
    byte[] bytes = new byte[entrySize];
    InputStream entryStream = zipfile.getInputStream(entry);
    for (int erg = entryStream.read(bytes); erg < bytes.length; erg += entryStream.read(bytes, erg, bytes.length - erg));
    return bytes;
  }

  /**
   * Returns a byte array of random length filled with random bytes.
   *
   * @author S3460
   * @return the random bytes
   */
  private byte[] getRandomBytes() {
    int lengt = randomSize(byteMaxLength);
    byte[] bytes = new byte[lengt];
    random.nextBytes(bytes);
    return bytes;
  }

  /**
   * Returns a random integer <= maxSize.
   *
   * @author S3460
   * @param maxSize the max size
   * @return the int
   */
  private int randomSize(int maxSize) {
    return ((int) (maxSize * random.nextFloat())) + 1;
  }

  /**
   * Compares the content of two zip files. The zip files are considered equal, if
   * the content of all zip entries is equal to the content of its corresponding entry
   * in the other zip file.
   *
   * @author S3460
   * @param zipSource the zip source
   * @param resultZip the result zip
   * @throws Exception the exception
   */
  private void compareFiles(ZipFile zipSource, ZipFile resultZip) throws Exception {
    boolean rc = false;
    try {
      for (Enumeration<ZipArchiveEntry> enumer = zipSource.getEntries(); enumer.hasMoreElements();) {
        ZipArchiveEntry sourceEntry = enumer.nextElement();
        ZipArchiveEntry resultEntry = resultZip.getEntry(sourceEntry.getName());
        assertNotNull("Entry nicht generiert: " + sourceEntry.getName(), resultEntry);
        byte[] oldBytes = toBytes(zipSource, sourceEntry);
        byte[] newBytes = toBytes(resultZip, resultEntry);
        rc = equal(oldBytes, newBytes);
        assertTrue("bytes the same " + sourceEntry, rc);
      }
    } finally {
      zipSource.close();
      resultZip.close();
    }
  }

  /**
   * Equal.
   *
   * @param source the source
   * @param target the target
   * @return true, if successful
   */
  public boolean equal(byte[] source, byte[] target) {
    if (source.length != target.length)
      return false;
    for (int i = 0; i < source.length; i++) {
      if (source[i] != target[i])
        return false;
    }
    return true;
  }

  /**
   * Uses JarDelta to create a patch file and tests if JarPatcher correctly reconstructs
   * newZip using this patch file.
   *
   * @author S3460
   * @param originalName the original name
   * @param targetName the target name
   * @param originalZip the original zip
   * @param newZip the new zip
   * @throws Exception the exception
   */
  private void runJarPatcher(String originalName, String targetName, ZipFile originalZip, ZipFile newZip) throws Exception {
    runJarPatcher(originalName, targetName, originalZip, newZip, true);
  }

  /**
   * Run jar patcher.
   *
   * @param originalName the original name
   * @param targetName the target name
   * @param originalZip the original zip
   * @param newZip the new zip
   * @param comparefiles the comparefiles
   * @throws Exception the exception
   */
  private void runJarPatcher(String originalName, String targetName, ZipFile originalZip, ZipFile newZip, boolean comparefiles) throws Exception {
    try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(new FileOutputStream(patchFile))) {
      new JarDelta().computeDelta(originalName, targetName, originalZip, newZip, output);
    }
    ZipFile patch = new ZipFile(patchFile);
    ZipArchiveEntry listEntry = patch.getEntry("META-INF/file.list");
    if (listEntry == null) {
      patch.close();
      throw new IOException("Invalid patch - list entry 'META-INF/file.list' not found");
    }
    BufferedReader patchlist = new BufferedReader(new InputStreamReader(patch.getInputStream(listEntry)));
    String next = patchlist.readLine();
    String sourceName = next;
    next = patchlist.readLine();
    new JarPatcher(patchFile.getName(), sourceName).applyDelta(patch, new ZipFile(originalName), new ZipArchiveOutputStream(new FileOutputStream(resultFile)), patchlist);
    if (comparefiles) {
      compareFiles(new ZipFile(targetName), new ZipFile(resultFile));
    }
  }

  /**
   * Run jar patcher derived file.
   *
   * @throws Exception the exception
   */
  private void runJarPatcherDerivedFile() throws Exception {
    ZipFile orginalZip = makeSourceZipFile(sourceFile);
    ZipFile derivedZip = makeTargetZipFile(orginalZip, targetFile);
    runJarPatcher(sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), orginalZip, derivedZip);
  }

  /**
   * Run jar patcher complete differnt file.
   *
   * @throws Exception the exception
   */
  private void runJarPatcherCompleteDifferntFile() throws Exception {
    ZipFile orginalZip = makeSourceZipFile(sourceFile);
    ZipFile derivedZip = makeSourceZipFile(targetFile);
    runJarPatcher(sourceFile.getAbsolutePath(), targetFile.getAbsolutePath(), orginalZip, derivedZip);
  }

  /**
   * Test jar patcher derived file.
   *
   * @throws Exception the exception
   */
  @Test
  public void testJarPatcherDerivedFile() throws Exception {
    //byteMaxLength = 10000;
    //entrySize = 10;
    runJarPatcherDerivedFile();
  }

  /**
   * Test jar patcher derived file big.
   *
   * @throws Exception the exception
   */
  @Test
  public void testJarPatcherDerivedFileBig() throws Exception {
    byteMaxLength = 100000;
    entryMaxSize = 100;
    runJarPatcherDerivedFile();
  }

  /**
   * No test jar patcher derived file very big.
   *
   * @throws Exception the exception
   */
  @Ignore
  public void noTestJarPatcherDerivedFileVeryBig() throws Exception {
    byteMaxLength = 100000;
    entryMaxSize = 100;
    runJarPatcherDerivedFile();
    for (int i = 0; i < 100; i++) {
      //runJarPatcherDerivedFile();
    }
  }

  /**
   * No test jar patcher derived file stressed.
   *
   * @throws Exception the exception
   */
  @Ignore
  public void noTestJarPatcherDerivedFileStressed() throws Exception {
    byteMaxLength = 100000;
    entryMaxSize = 1000;
    runJarPatcherDerivedFile();
  }

  /**
   * Test jar patcher complete differnt file.
   *
   * @throws Exception the exception
   */
  @Test
  public void testJarPatcherCompleteDifferntFile() throws Exception {
    runJarPatcherCompleteDifferntFile();
  }

  /**
   * Test jar patcher complete differnt file big.
   *
   * @throws Exception the exception
   */
  @Test
  public void testJarPatcherCompleteDifferntFileBig() throws Exception {
    byteMaxLength = 10000;
    entryMaxSize = 100;
    runJarPatcherCompleteDifferntFile();
  }

  // Throws exception from patching if target and output crc:s don't match except for zip files, 
  // which may get different crc's due to different compression options
  /**
   * Test embedded zip.
   *
   * @throws Exception the exception
   */
  @Test
  public void testEmbeddedZip() throws Exception {
    String file1 = "src/test/resources/embedded1.zip";
    String file2 = "src/test/resources/embedded2.zip";
    runJarPatcher(file1, file2, new ZipFile(file1), new ZipFile(file2), false);
  }

  /**
   * No test jar patcher complete differnt stressed.
   *
   * @throws Exception the exception
   */
  @Ignore
  public void noTestJarPatcherCompleteDifferntStressed() throws Exception {
    byteMaxLength = 100000;
    entryMaxSize = 1000;
    runJarPatcherCompleteDifferntFile();
  }

  /**
   * Tests JarDelta and JarPatcher on two identical files.
   *
   * @throws Exception the exception
   */
  @Test
  public void testJarPatcherIdentFile() throws Exception {
    ZipFile originalZip = makeSourceZipFile(sourceFile);
    new JarDelta().computeDelta(sourceFile.getAbsolutePath(), sourceFile.getAbsolutePath(), originalZip, originalZip, new ZipArchiveOutputStream(new FileOutputStream(patchFile)));
    ZipFile patch = new ZipFile(patchFile);
    ZipArchiveEntry listEntry = patch.getEntry("META-INF/file.list");
    if (listEntry == null) {
      patch.close();
      throw new IOException("Invalid patch - list entry 'META-INF/file.list' not found");
    }
    BufferedReader patchlist = new BufferedReader(new InputStreamReader(patch.getInputStream(listEntry)));
    String next = patchlist.readLine();
    String sourceName = next;
    next = patchlist.readLine();
    ZipFile source = new ZipFile(sourceFile);
    new JarPatcher(patchFile.getName(), sourceName).applyDelta(patch, source, new ZipArchiveOutputStream(new FileOutputStream(resultFile)), patchlist);
    compareFiles(new ZipFile(sourceFile), new ZipFile(resultFile));
  }

  /**
   * Tests JarDelta and JarPatcher on two big identical files.
   *
   * @throws Exception the exception
   */
  @Ignore
  public void noTestJarPatcherIdentFileBig() throws Exception {
    byteMaxLength = 100000;
    entryMaxSize = 1000;
    testJarPatcherIdentFile();
  }
}
