package com.nothome.delta.text;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.CharBuffer;

import org.junit.Test;

/**
 * The Class ChecksumTest.
 */
public class ChecksumTest {
  /**
   * For file.
   *
   * @param name the name
   * @return the reader
   * @throws FileNotFoundException the file not found exception
   */
  Reader forFile(String name) throws FileNotFoundException {
    InputStreamReader isr = new InputStreamReader(getClass().getResourceAsStream(name));
    return new BufferedReader(isr);
  }

  /**
   * Test ver.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Test
  public void testVer() throws IOException {
    int d = Delta.DEFAULT_CHUNK_SIZE;
    Checksum cs = new Checksum(DeltaTest.forFile("/ver1.txt"), d);
    String s = "xxx yyy zzz\r\nxxx yyy zzz\r\nxxx yyy zzz".substring(0, d);
    long queryChecksum = Checksum.queryChecksum(CharBuffer.wrap(s), d);
    System.out.println(cs);
    System.out.println(queryChecksum);
    Integer i = cs.findChecksumIndex(queryChecksum);
    assertEquals(Integer.valueOf(0), i);
  }
}
