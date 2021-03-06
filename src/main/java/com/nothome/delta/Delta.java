/*
 *
 * Copyright (c) 2001 Torgeir Veimo
 * Copyright (c) 2002 Nicolas PERIDONT
 * Bug Fixes: Daniel Morrione dan@morrione.net
 * Copyright (c) 2006 Heiko Klein
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
 * Change Log:
 * iiimmddyyn  nnnnn  Description
 * ----------  -----  -------------------------------------------------------
 * gls100603a         Fixes from Torgeir Veimo and Dan Morrione
 * gls110603a         Stream not being closed thus preventing a file from
 *                       being subsequently deleted.
 * gls031504a         Error being written to stderr rather than throwing exception
 */
package com.nothome.delta;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Class for computing deltas against a source.
 * The source file is read by blocks and a hash is computed per block.
 * Then the target is scanned for matching blocks.
 * <p>
 * This class is not thread safe. Use one instance per thread.
 * <p>
 * This class should support files over 4GB in length, although you must
 * use a larger checksum size, such as 1K, as all checksums use "int" indexing.
 * Newer versions may eventually support paging in/out of checksums.
 */
public class Delta {
  /**
   * Debug flag.
   */
  final static boolean debug = false;
  /**
   * Default size of 16.
   * For "Lorem ipsum" text files (see the tests) the ideal size is about 14.
   * Any smaller and the patch size becomes actually be larger.
   * <p>
   * Use a size like 64 or 128 for large files.
   */
  public static final int DEFAULT_CHUNK_SIZE = 1 << 4;
  /**
   * Chunk Size.
   */
  private int S;
  /** The source. */
  private SourceState source;
  /** The target. */
  private TargetState target;
  /** The output. */
  private DiffWriter output;

  /**
   * Constructs a new Delta.
   * In the future, additional constructor arguments will set the algorithm details.
   */
  public Delta() {
    setChunkSize(DEFAULT_CHUNK_SIZE);
  }

  /**
   * Sets the chunk size used.
   * Larger chunks are faster and use less memory, but create larger patches
   * as well.
   *
   * @param size the new chunk size
   */
  public void setChunkSize(int size) {
    if (size <= 0)
      throw new IllegalArgumentException("Invalid size");
    S = size;
  }

  /**
   * Compares the source bytes with target bytes, writing to output.
   *
   * @param source the source
   * @param target the target
   * @param output the output
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void compute(byte source[], byte target[], OutputStream output) throws IOException {
    compute(new ByteBufferSeekableSource(source), new ByteArrayInputStream(target), new GDiffWriter(output));
  }

  /**
   * Compares the source bytes with target bytes, returning output.
   *
   * @param source the source
   * @param target the target
   * @return the byte[]
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public byte[] compute(byte source[], byte target[]) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    compute(source, target, os);
    return os.toByteArray();
  }

  /**
   * Compares the source bytes with target input, writing to output.
   *
   * @param sourceBytes the source bytes
   * @param inputStream the input stream
   * @param diffWriter the diff writer
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void compute(byte[] sourceBytes, InputStream inputStream, DiffWriter diffWriter) throws IOException {
    compute(new ByteBufferSeekableSource(sourceBytes), inputStream, diffWriter);
  }

  /**
   * Compares the source file with a target file, writing to output.
   *
   * @param sourceFile the source file
   * @param targetFile the target file
   * @param output will be closed
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void compute(File sourceFile, File targetFile, DiffWriter output) throws IOException {
    RandomAccessFileSeekableSource source = new RandomAccessFileSeekableSource(new RandomAccessFile(sourceFile, "r"));
    InputStream is = new BufferedInputStream(new FileInputStream(targetFile));
    try {
      compute(source, is, output);
    } finally {
      source.close();
      is.close();
    }
  }

  /**
   * Compares the source with a target, writing to output.
   *
   * @param seekSource the seek source
   * @param targetIS the target is
   * @param output will be closed
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void compute(SeekableSource seekSource, InputStream targetIS, DiffWriter output) throws IOException {
    if (debug) {
      debug("using match length S = " + S);
    }
    source = new SourceState(seekSource);
    target = new TargetState(targetIS);
    this.output = output;
    if (debug)
      debug("checksums " + source.checksum);
    while (!target.eof()) {
      debug("!target.eof()");
      int index = target.find(source);
      if (index != -1) {
        if (debug)
          debug("found hash " + index);
        long offset = (long) index * S;
        source.seek(offset);
        int match = target.longestMatch(source);
        if (match >= S) {
          if (debug)
            debug("output.addCopy(" + offset + "," + match + ")");
          output.addCopy(offset, match);
        } else {
          // move the position back according to how much we can't copy
          target.tbuf.position(target.tbuf.position() - match);
          addData();
        }
      } else {
        addData();
      }
    }
    output.close();
  }

  /**
   * Adds the data.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  private void addData() throws IOException {
    int i = target.read();
    if (debug)
      debug("addData " + Integer.toHexString(i));
    if (i == -1)
      return;
    output.addData((byte) i);
  }

  /**
   * The Class SourceState.
   */
  class SourceState {
    /** The checksum. */
    private Checksum checksum;
    /** The source. */
    private SeekableSource source;

    /**
     * Instantiates a new source state.
     *
     * @param source the source
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public SourceState(SeekableSource source) throws IOException {
      checksum = new Checksum(source, S);
      this.source = source;
      source.seek(0);
    }

    /**
     * Seek.
     *
     * @param index the index
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void seek(long index) throws IOException {
      source.seek(index);
    }

    /**
     * Returns a debug <code>String</code>.
     *
     * @return the string
     */
    @Override
    public String toString() {
      return "Source" + " checksum=" + this.checksum + " source=" + this.source + "";
    }
  }

  /**
   * The Class TargetState.
   */
  class TargetState {
    /** The c. */
    private ReadableByteChannel c;
    /** The tbuf. */
    private ByteBuffer tbuf = ByteBuffer.allocate(blocksize());
    /** The sbuf. */
    private ByteBuffer sbuf = ByteBuffer.allocate(blocksize());
    /** The hash. */
    private long hash;
    /** The hash reset. */
    private boolean hashReset = true;
    /** The eof. */
    private boolean eof;

    /**
     * Instantiates a new target state.
     *
     * @param targetIS the target is
     * @throws IOException Signals that an I/O exception has occurred.
     */
    TargetState(InputStream targetIS) throws IOException {
      c = Channels.newChannel(targetIS);
      tbuf.limit(0);
    }

    /**
     * Blocksize.
     *
     * @return the int
     */
    private int blocksize() {
      return Math.min(1024 * 16, S * 4);
    }

    /**
     * Returns the index of the next N bytes of the stream.
     *
     * @param source the source
     * @return the int
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public int find(SourceState source) throws IOException {
      if (eof)
        return -1;
      sbuf.clear();
      sbuf.limit(0);
      if (hashReset) {
        debug("hashReset");
        while (tbuf.remaining() < S) {
          tbuf.compact();
          int read = c.read(tbuf);
          tbuf.flip();
          if (read == -1) {
            debug("target ending");
            return -1;
          }
        }
        hash = Checksum.queryChecksum(tbuf, S);
        hashReset = false;
      }
      if (debug)
        debug("hash " + hash + " " + dump());
      return source.checksum.findChecksumIndex(hash);
    }

    /**
     * Eof.
     *
     * @return true, if successful
     */
    public boolean eof() {
      return eof;
    }

    /**
     * Reads a byte.
     *
     * @return the int
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public int read() throws IOException {
      if (tbuf.remaining() <= S) {
        readMore();
        if (!tbuf.hasRemaining()) {
          eof = true;
          return -1;
        }
      }
      byte b = tbuf.get();
      if (tbuf.remaining() >= S) {
        byte nchar = tbuf.get(tbuf.position() + S - 1);
        hash = Checksum.incrementChecksum(hash, b, nchar, S);
      } else {
        debug("out of char");
      }
      return b & 0xFF;
    }

    /**
     * Returns the longest match length at the source location.
     *
     * @param source the source
     * @return the int
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public int longestMatch(SourceState source) throws IOException {
      debug("longestMatch");
      int match = 0;
      hashReset = true;
      while (true) {
        if (!sbuf.hasRemaining()) {
          sbuf.clear();
          int read = source.source.read(sbuf);
          sbuf.flip();
          if (read == -1)
            return match;
        }
        if (!tbuf.hasRemaining()) {
          readMore();
          if (!tbuf.hasRemaining()) {
            debug("target ending");
            eof = true;
            return match;
          }
        }
        if (sbuf.get() != tbuf.get()) {
          tbuf.position(tbuf.position() - 1);
          return match;
        }
        match++;
      }
    }

    /**
     * Read more.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private void readMore() throws IOException {
      if (debug)
        debug("readMore " + tbuf);
      tbuf.compact();
      c.read(tbuf);
      tbuf.flip();
    }

    /**
     * Hash.
     */
    void hash() {
      hash = Checksum.queryChecksum(tbuf, S);
    }

    /**
     * Returns a debug <code>String</code>.
     *
     * @return the string
     */
    @Override
    public String toString() {
      return "Target[" + " targetBuff=" + dump() + // this.tbuf +
        " sourceBuff=" + this.sbuf + " hashf=" + this.hash + " eof=" + this.eof + "]";
    }

    /**
     * Dump.
     *
     * @return the string
     */
    private String dump() {
      return dump(tbuf);
    }

    /**
     * Dump.
     *
     * @param bb the bb
     * @return the string
     */
    private String dump(ByteBuffer bb) {
      return getTextDump(bb);
    }

    /**
     * Append.
     *
     * @param sb the sb
     * @param value the value
     */
    private void append(StringBuffer sb, int value) {
      char b1 = (char) ((value >> 4) & 0x0F);
      char b2 = (char) ((value) & 0x0F);
      sb.append(Character.forDigit(b1, 16));
      sb.append(Character.forDigit(b2, 16));
    }

    /**
     * Gets the text dump.
     *
     * @param bb the bb
     * @return the text dump
     */
    public String getTextDump(ByteBuffer bb) {
      StringBuffer sb = new StringBuffer(bb.remaining() * 2);
      bb.mark();
      while (bb.hasRemaining()) {
        int val = bb.get();
        if (val > 32 && val < 127)
          sb.append(" ").append((char) val);
        else
          append(sb, val);
      }
      bb.reset();
      return sb.toString();
    }
  }

  /**
   * Creates a patch using file names.
   *
   * @param argv the arguments
   * @throws Exception the exception
   */
  public static void main(String argv[]) throws Exception {
    if (argv.length != 3) {
      System.err.println("usage Delta [-d] source target [output]");
      System.err.println("either -d or an output filename must be specified.");
      System.err.println("aborting..");
      return;
    }
    DiffWriter output;
    File sourceFile;
    File targetFile;
    if (argv[0].equals("-d")) {
      sourceFile = new File(argv[1]);
      targetFile = new File(argv[2]);
      output = new DebugDiffWriter();
    } else {
      sourceFile = new File(argv[0]);
      targetFile = new File(argv[1]);
      output = new GDiffWriter(new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(argv[2])))));
    }
    if (sourceFile.length() > Integer.MAX_VALUE || targetFile.length() > Integer.MAX_VALUE) {
      System.err.println("source or target is too large, max length is " + Integer.MAX_VALUE);
      System.err.println("aborting..");
      output.close();
      return;
    }
    Delta d = new Delta();
    d.compute(sourceFile, targetFile, output);
    output.flush();
    output.close();
    if (debug) //gls031504a
      System.out.println("finished generating delta");
  }

  /**
   * Debug.
   *
   * @param s the s
   */
  private void debug(String s) {
    if (debug)
      System.err.println(s);
  }
}
