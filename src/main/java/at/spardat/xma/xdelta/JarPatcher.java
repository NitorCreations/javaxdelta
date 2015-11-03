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
package at.spardat.xma.xdelta;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipException;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

import com.nothome.delta.GDiffPatcher;
import com.nothome.delta.PatchException;

/**
 * This class applys a zip file containing deltas created with {@link JarDelta} using
 * {@link com.nothome.delta.GDiffPatcher} on the files contained in the jar file.
 * The result of this operation is not binary equal to the original target zip file.
 * Timestamps of files and directories are not reconstructed. But the contents of all
 * files in the reconstructed target zip file are complely equal to their originals.
 *
 * @author s2877
 */
public class JarPatcher {
  /** The patch name. */
  private final String patchName;
  /** The source name. */
  private final String sourceName;
  /** The buffer. */
  private final byte[] buffer = new byte[8 * 1024];
  /** The next. */
  private String next = null;

  /**
   * Applies the differences in patch to source to create the target file. All binary difference files
   * are applied to their corresponding file in source using {@link com.nothome.delta.GDiffPatcher}.
   * All other files listed in <code>META-INF/file.list</code> are copied from patch to output.
   *
   * @param patch a zip file created by {@link JarDelta#computeDelta(String, String, ZipFile, ZipFile, ZipArchiveOutputStream)}
   *        containing the patches to apply
   * @param source the original zip file, where the patches have to be applied
   * @param output the patched zip file to create
   * @param list the list
   * @throws IOException if an error occures reading or writing any entry in a zip file
   */
  public void applyDelta(ZipFile patch, ZipFile source, ZipArchiveOutputStream output, BufferedReader list) throws IOException {
    applyDelta(patch, source, output, list, "");
    patch.close();
  }

  /**
   * Apply delta.
   *
   * @param patch the patch
   * @param source the source
   * @param output the output
   * @param list the list
   * @param prefix the prefix
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void applyDelta(ZipFile patch, ZipFile source, ZipArchiveOutputStream output, BufferedReader list, String prefix) throws IOException {
    String fileName = null;
    try {
      for (fileName = (next == null ? list.readLine() : next); fileName != null; fileName = (next == null ? list.readLine() : next)) {
        if (next != null)
          next = null;
        if (!fileName.startsWith(prefix)) {
          next = fileName;
          return;
        }
        int crcDelim = fileName.lastIndexOf(':');
        int crcStart = fileName.lastIndexOf('|');
        long crc = Long.valueOf(fileName.substring(crcStart + 1, crcDelim), 16);
        long crcSrc = Long.valueOf(fileName.substring(crcDelim + 1), 16);
        fileName = fileName.substring(prefix.length(), crcStart);
        if ("META-INF/file.list".equalsIgnoreCase(fileName))
          continue;
        if (fileName.contains("!")) {
          String[] embeds = fileName.split("\\!");
          ZipArchiveEntry original = getEntry(source, embeds[0], crcSrc);
          File originalFile = File.createTempFile("jardelta-tmp-origin-", ".zip");
          File outputFile = File.createTempFile("jardelta-tmp-output-", ".zip");
          Exception thrown = null;
          try (FileOutputStream out = new FileOutputStream(originalFile); InputStream in = source.getInputStream(original)) {
            int read = 0;
            while (-1 < (read = in.read(buffer))) {
              out.write(buffer, 0, read);
            }
            out.flush();
            applyDelta(patch, new ZipFile(originalFile), new ZipArchiveOutputStream(outputFile), list, prefix + embeds[0] + "!");
          } catch (Exception e) {
            thrown = e;
            throw e;
          } finally {
            originalFile.delete();
            try (FileInputStream in = new FileInputStream(outputFile)) {
              if (thrown == null) {
                ZipArchiveEntry outEntry = copyEntry(original);
                output.putArchiveEntry(outEntry);
                int read = 0;
                while (-1 < (read = in.read(buffer))) {
                  output.write(buffer, 0, read);
                }
                output.flush();
                output.closeArchiveEntry();
              }
            } finally {
              outputFile.delete();
            }
          }
        } else {
          try {
            ZipArchiveEntry patchEntry = getEntry(patch, prefix + fileName, crc);
            if (patchEntry != null) { // new Entry
              ZipArchiveEntry outputEntry = JarDelta.entryToNewName(patchEntry, fileName);
              output.putArchiveEntry(outputEntry);
              if (!patchEntry.isDirectory()) {
                try (InputStream in = patch.getInputStream(patchEntry)) {
                  int read = 0;
                  while (-1 < (read = in.read(buffer))) {
                    output.write(buffer, 0, read);
                  }
                }
              }
              closeEntry(output, outputEntry, crc);
            } else {
              ZipArchiveEntry sourceEntry = getEntry(source, fileName, crcSrc);
              if (sourceEntry == null) {
                throw new FileNotFoundException(fileName + " not found in " + sourceName + " or " + patchName);
              }
              if (sourceEntry.isDirectory()) {
                ZipArchiveEntry outputEntry = new ZipArchiveEntry(sourceEntry);
                output.putArchiveEntry(outputEntry);
                closeEntry(output, outputEntry, crc);
                continue;
              }
              patchEntry = getPatchEntry(patch, prefix + fileName + ".gdiff", crc);
              if (patchEntry != null) { // changed Entry
                ZipArchiveEntry outputEntry = new ZipArchiveEntry(sourceEntry);
                outputEntry.setTime(patchEntry.getTime());
                output.putArchiveEntry(outputEntry);
                byte[] sourceBytes = new byte[(int) sourceEntry.getSize()];
                try (InputStream sourceStream = source.getInputStream(sourceEntry)) {
                  for (int erg = sourceStream.read(sourceBytes); erg < sourceBytes.length; erg += sourceStream.read(sourceBytes, erg, sourceBytes.length - erg));
                }
                InputStream patchStream = patch.getInputStream(patchEntry);
                GDiffPatcher diffPatcher = new GDiffPatcher();
                diffPatcher.patch(sourceBytes, patchStream, output);
                patchStream.close();
                outputEntry.setCrc(crc);
                closeEntry(output, outputEntry, crc);
              } else { // unchanged Entry
                ZipArchiveEntry outputEntry = new ZipArchiveEntry(sourceEntry);
                if (JarDelta.zipFilesPattern.matcher(sourceEntry.getName()).matches()) {
                    crc = sourceEntry.getCrc();
                  }
                output.putArchiveEntry(outputEntry);
                try (InputStream in = source.getInputStream(sourceEntry)) {
                  int read = 0;
                  while (-1 < (read = in.read(buffer))) {
                    output.write(buffer, 0, read);
                  }
                }
                output.flush();
                closeEntry(output, outputEntry, crc);
              }
            }
          } catch (PatchException pe) {
            IOException ioe = new IOException();
            ioe.initCause(pe);
            throw ioe;
          }
        }
      }
    } catch (Exception e) {
      System.err.println(prefix + fileName);
      throw e;
    } finally {
      source.close();
      output.close();
    }
  }

  /**
   * Gets the entry.
   *
   * @param source the source
   * @param name the name
   * @param crc the crc
   * @return the entry
   */
  private ZipArchiveEntry getEntry(ZipFile source, String name, long crc) {
    for (ZipArchiveEntry next : source.getEntries(name)) {
      if (next.getCrc() == crc)
        return next;
    }
    if (!JarDelta.zipFilesPattern.matcher(name).matches()) {
      return null;
    } else {
      return source.getEntry(name);
    }
  }

  /**
   * Gets the patch entry.
   *
   * @param source the source
   * @param name the name
   * @param crc the crc
   * @return the patch entry
   */
  private ZipArchiveEntry getPatchEntry(ZipFile source, String name, long crc) {
    for (ZipArchiveEntry next : source.getEntries(name)) {
      long nextCrc = Long.parseLong(next.getComment());
      if (nextCrc == crc)
        return next;
    }
    return null;
  }

  /**
   * Close entry.
   *
   * @param output the output
   * @param outEntry the out entry
   * @param crc the crc
   * @throws IOException Signals that an I/O exception has occurred.
   */
  private void closeEntry(ZipArchiveOutputStream output, ZipArchiveEntry outEntry, long crc) throws IOException {
    output.flush();
    output.closeArchiveEntry();
    if (outEntry.getCrc() != crc)
      throw new IOException("CRC mismatch for " + outEntry.getName());
  }

  /**
   * Instantiates a new jar patcher.
   *
   * @param patchName the patch name
   * @param sourceName the source name
   */
  public JarPatcher(String patchName, String sourceName) {
    this.patchName = patchName;
    this.sourceName = sourceName;
  }

  /**
   * Main method to make {@link #applyDelta(ZipFile, ZipFile, ZipArchiveOutputStream, BufferedReader)} available at
   * the command line.<br>
   * usage JarPatcher source patch output
   *
   * @param args the arguments
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static void main(String[] args) throws IOException {
    String patchName = null;
    String outputName = null;
    String sourceName = null;
    if (args.length == 0) {
      System.err.println("usage JarPatcher patch [output [source]]");
      System.exit(1);
    } else {
      patchName = args[0];
      if (args.length > 1) {
        outputName = args[1];
        if (args.length > 2) {
          sourceName = args[2];
        }
      }
    }
    ZipFile patch = new ZipFile(patchName);
    ZipArchiveEntry listEntry = patch.getEntry("META-INF/file.list");
    if (listEntry == null) {
      System.err.println("Invalid patch - list entry 'META-INF/file.list' not found");
      System.exit(2);
    }
    BufferedReader list = new BufferedReader(new InputStreamReader(patch.getInputStream(listEntry)));
    String next = list.readLine();
    if (sourceName == null) {
      sourceName = next;
    }
    next = list.readLine();
    if (outputName == null) {
      outputName = next;
    }
    int ignoreSourcePaths = Integer.parseInt(System.getProperty("patcher.ignoreSourcePathElements", "0"));
    int ignoreOutputPaths = Integer.parseInt(System.getProperty("patcher.ignoreOutputPathElements", "0"));
    Path sourcePath = Paths.get(sourceName);
    Path outputPath = Paths.get(outputName);
    if (ignoreOutputPaths >= outputPath.getNameCount()) {
      patch.close();
      StringBuilder b = new StringBuilder().append("Not enough path elements to ignore in output (").append(ignoreOutputPaths).append(" in ").append(outputName).append(")");
      throw new IOException(b.toString());
    }
    if (ignoreSourcePaths >= sourcePath.getNameCount()) {
      patch.close();
      StringBuilder b = new StringBuilder().append("Not enough path elements to ignore in source (").append(sourcePath).append(" in ").append(sourceName).append(")");
      throw new IOException(b.toString());
    }
    if (ignoreSourcePaths > 0) {
      sourcePath = sourcePath.subpath(ignoreSourcePaths, sourcePath.getNameCount());
    }
    if (ignoreOutputPaths > 0) {
      outputPath = outputPath.subpath(ignoreOutputPaths, outputPath.getNameCount());
    }
    File sourceFile = sourcePath.toFile();
    File outputFile = outputPath.toFile();
    if (!(outputFile.getAbsoluteFile().getParentFile().mkdirs() || outputFile.getAbsoluteFile().getParentFile().exists())) {
      patch.close();
      throw new IOException("Failed to create " + outputFile.getAbsolutePath());
    }
    new JarPatcher(patchName, sourceFile.getName()).applyDelta(patch, new ZipFile(sourceFile), new ZipArchiveOutputStream(new FileOutputStream(outputFile)), list);
    list.close();
  }

  /**
   * Entry to new name.
   *
   * @param source the source
   * @param name the name
   * @return the zip archive entry
   * @throws ZipException the zip exception
   */
  private ZipArchiveEntry copyEntry(ZipArchiveEntry source) throws ZipException {
    ZipArchiveEntry ret = new ZipArchiveEntry(source.getName());
    byte[] extra = source.getExtra();
    if (extra != null) {
      ret.setExtraFields(ExtraFieldUtils.parse(extra, true, ExtraFieldUtils.UnparseableExtraField.READ));
    } else {
      ret.setExtra(ExtraFieldUtils.mergeLocalFileDataData(source.getExtraFields(true)));
    }
    ret.setInternalAttributes(source.getInternalAttributes());
    ret.setExternalAttributes(source.getExternalAttributes());
    ret.setExtraFields(source.getExtraFields(true));
    return ret;
  }
}
