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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

import com.nothome.delta.Delta;
import com.nothome.delta.DiffWriter;
import com.nothome.delta.GDiffWriter;
/**
 * This class calculates the binary difference of two zip files by applying {@link com.nothome.delta.Delta}
 * to all files contained in both zip files. All these binary differences are stored in the output zip file.
 * New files are simply copied to the output zip file. Additionally all files contained in the target zip
 * file are listed in <code>META-INF/file.list</code>.<p>
 * Use {@link JarPatcher} to apply the output zip file.<p>
 *
 * @author gruber
 */
public class JarDelta {
	
	/** The Constant zipFilesPattern. */
	private static final Pattern zipFilesPattern = Pattern.compile(".*?\\.zip$|.*?\\.jar$|.*?\\.war$|.*?\\.ear$", Pattern.CASE_INSENSITIVE);
	
	/** The Constant BUFFER_LEN. */
	private static final int BUFFER_LEN = 8 * 1024;
    
    /** The buffer. */
    private final byte[] buffer = new byte[BUFFER_LEN];
    
    /** The calculated delta. */
    private byte[] calculatedDelta = null;
	/**
     * Computes the binary differences of two zip files. For all files contained in source and target which
     * are not equal, the binary difference is caluclated by using
     * {@link com.nothome.delta.Delta#compute(byte[], InputStream, DiffWriter)}.
     * If the files are equal, nothing is written to the output for them.
     * Files contained only in target and files to small for {@link com.nothome.delta.Delta} are copied to output.
     * Files contained only in source are ignored.
     * At last a list of all files contained in target is written to <code>META-INF/file.list</code> in output.
     *
     * @param sourceName the original zip file
     * @param targetName a modification of the original zip file
     * @param source the original zip file
     * @param target a modification of the original zip file
     * @param output the zip file where the patches have to be written to
     * @throws IOException if an error occurs reading or writing any entry in a zip file
     */
	public void computeDelta(String sourceName, String targetName, ZipFile source, ZipFile target, ZipArchiveOutputStream output) throws IOException {
            ByteArrayOutputStream listBytes = new ByteArrayOutputStream();
            PrintWriter list = new PrintWriter(new OutputStreamWriter(listBytes));
            list.println(sourceName);
            list.println(targetName);
        	computeDelta(source, target, output, list, "");
        	list.close();
        	ZipArchiveEntry listEntry = new ZipArchiveEntry("META-INF/file.list");
        	output.putArchiveEntry(listEntry);
        	output.write(listBytes.toByteArray());
        	output.closeArchiveEntry();
        	output.finish();
        	output.flush();
    }

	/**
	 * Compute delta.
	 *
	 * @param source the source
	 * @param target the target
	 * @param output the output
	 * @param list the list
	 * @param prefix the prefix
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void computeDelta(ZipFile source, ZipFile target, ZipArchiveOutputStream output, PrintWriter list, String prefix) throws IOException {
        try {
    		for(Enumeration<ZipArchiveEntry> enumer=target.getEntries();enumer.hasMoreElements();) {
    			calculatedDelta = null;
    			ZipArchiveEntry targetEntry = enumer.nextElement();
                ZipArchiveEntry sourceEntry = findBestSource(source, target, targetEntry);
                String nextEntryName = prefix + targetEntry.getName(); 
            	if (sourceEntry != null && targetEntry != null && zipFilesPattern.matcher(sourceEntry.getName()).matches() && !equal(sourceEntry, targetEntry)) {
            		nextEntryName += "!";
            	}
            	nextEntryName += "|" + targetEntry.getCrc();
            	if (sourceEntry != null) {
                	nextEntryName += ":" + sourceEntry.getCrc();
            	} else {
            		nextEntryName += ":0"; 
            	}
        		list.println(nextEntryName);
                if(targetEntry.isDirectory()) {
                    if(sourceEntry==null) {
                        ZipArchiveEntry outputEntry = entryToNewName(targetEntry, prefix + targetEntry.getName());
                        output.putArchiveEntry(outputEntry);
                        output.closeArchiveEntry();
                    }
                } else {
                	if(sourceEntry==null
                			|| sourceEntry.getSize() <= Delta.DEFAULT_CHUNK_SIZE
                			|| targetEntry.getSize() <= Delta.DEFAULT_CHUNK_SIZE) {  // new Entry od. alter Eintrag od. neuer Eintrag leer
                		ZipArchiveEntry outputEntry = entryToNewName(targetEntry, prefix + targetEntry.getName());
                		output.putArchiveEntry(outputEntry);
                		try (InputStream in = target.getInputStream(targetEntry)) {
                			int read = 0;
                			while (-1 < (read = in.read(buffer))) {
                				output.write(buffer, 0, read);
                			}
                			output.flush();
                		}
                		output.closeArchiveEntry();
                	} else {
                		if(!equal(sourceEntry,targetEntry)) {
                			if (zipFilesPattern.matcher(sourceEntry.getName()).matches()) {
                				File embeddedSource = File.createTempFile("jardelta-tmp", ".zip");
                				try (FileOutputStream out = new FileOutputStream(embeddedSource); 
                						InputStream in = source.getInputStream(sourceEntry)) {
                					int read = 0;
                					while (-1 < (read = in.read(buffer))) {
                						out.write(buffer, 0, read);
                					}
                					out.flush();
                				}
                				File embeddedTarget = File.createTempFile("jardelta-tmp", ".zip");
                				try (FileOutputStream out = new FileOutputStream(embeddedTarget); 
                						InputStream in = target.getInputStream(targetEntry)) {
                					int read = 0;
                					while (-1 < (read = in.read(buffer))) {
                						out.write(buffer, 0, read);
                					}
                					out.flush();
                				}
                				computeDelta(new ZipFile(embeddedSource), new ZipFile(embeddedTarget), output, list, prefix + sourceEntry.getName() + "!");
                				embeddedSource.delete();
                				embeddedTarget.delete();
                			} else {
                				ZipArchiveEntry outputEntry = new ZipArchiveEntry(prefix + targetEntry.getName()+".gdiff");
                				outputEntry.setTime(targetEntry.getTime());
                				outputEntry.setComment("" + targetEntry.getCrc());
                				output.putArchiveEntry(outputEntry);
                				if (calculatedDelta != null) {
                					output.write(calculatedDelta);
                					output.flush();
                				} else {
                					try (ByteArrayOutputStream outbytes = new ByteArrayOutputStream()) {
                						Delta d = new Delta();
                						DiffWriter diffWriter = new GDiffWriter(new DataOutputStream(outbytes));
                						int sourceSize = (int)sourceEntry.getSize();
                						byte[] sourceBytes = new byte[sourceSize];
                						try (InputStream sourceStream = source.getInputStream(sourceEntry)) {
                							for(int erg=sourceStream.read(sourceBytes);erg<sourceBytes.length;erg+=sourceStream.read(sourceBytes,erg,sourceBytes.length-erg));
                						}
                						d.compute(sourceBytes,target.getInputStream(targetEntry),diffWriter);
                						output.write(outbytes.toByteArray());
                					}
                				}
                				output.closeArchiveEntry();
                			}
                		}
                	}
                }
    		}
        } finally {
            source.close();
            target.close();
        }
	}
	
    /**
     * Test if the content of two byte arrays is completly identical.
     *
     * @param sourceEntry the source entry
     * @param targetEntry the target entry
     * @return true if source and target contain the same bytes.
     */
	public boolean equal(ZipArchiveEntry sourceEntry, ZipArchiveEntry targetEntry) {
        return (sourceEntry.getSize() == targetEntry.getSize()) &&
        		(sourceEntry.getCrc() == targetEntry.getCrc());
    }
	
	/**
	 * Find best source.
	 *
	 * @param source the source
	 * @param target the target
	 * @param targetEntry the target entry
	 * @return the zip archive entry
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public ZipArchiveEntry findBestSource(ZipFile source, ZipFile target, ZipArchiveEntry targetEntry) throws IOException {
		ArrayList<ZipArchiveEntry> ret = new ArrayList<>();
		for (ZipArchiveEntry next : source.getEntries(targetEntry.getName())) {
			if (next.getCrc() == targetEntry.getCrc()) return next;
			ret.add(next);
		}
		if (ret.size() == 0) return null;
		if (ret.size() == 1 || targetEntry.isDirectory()) return ret.get(0);
		//More than one and no matching crc --- need to calculate xdeltas and pick the  shortest
		ZipArchiveEntry retEntry = null;
		for (ZipArchiveEntry sourceEntry : ret) {
			try (ByteArrayOutputStream outbytes = new ByteArrayOutputStream()) {
				Delta d = new Delta();
				DiffWriter diffWriter = new GDiffWriter(new DataOutputStream(outbytes));
				int sourceSize = (int)sourceEntry.getSize();
				byte[] sourceBytes = new byte[sourceSize];
				try (InputStream sourceStream = source.getInputStream(sourceEntry)) {
					for(int erg=sourceStream.read(sourceBytes);erg<sourceBytes.length;erg+=sourceStream.read(sourceBytes,erg,sourceBytes.length-erg));
				}
				d.compute(sourceBytes, target.getInputStream(targetEntry), diffWriter);
				byte[] nextDiff = outbytes.toByteArray();
				if (calculatedDelta == null || calculatedDelta.length > nextDiff.length) {
					retEntry = sourceEntry;
					calculatedDelta = nextDiff;
				}
			}
		}
		return retEntry;
	}
	
	/**
	 * Main method to make {@link #computeDelta(String, String, ZipFile, ZipFile, ZipArchiveOutputStream)} available at
	 * the command line.<br>
	 * usage JarDelta source target output
	 *
	 * @param args the arguments
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.err.println("usage JarDelta source target output");
			return;
		}
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(new FileOutputStream(args[2]))) {
        	new JarDelta().computeDelta(args[0], args[1], new ZipFile(args[0]), new ZipFile(args[1]), output);
        }
	}
	
	/**
	 * Entry to new name.
	 *
	 * @param source the source
	 * @param name the name
	 * @return the zip archive entry
	 * @throws ZipException the zip exception
	 */
	private ZipArchiveEntry entryToNewName(ZipArchiveEntry source, String name) throws ZipException {
		if (source.getName().equals(name)) return new ZipArchiveEntry(source);
		ZipArchiveEntry ret = new ZipArchiveEntry(name);
        byte[] extra = source.getExtra();
        if (extra != null) {
            ret.setExtraFields(ExtraFieldUtils.parse(extra, true,
                                                 ExtraFieldUtils
                                                 .UnparseableExtraField.READ));
        } else {
            ret.setExtra(ExtraFieldUtils.mergeLocalFileDataData(source.getExtraFields(true)));
        }
        ret.setInternalAttributes(source.getInternalAttributes());
        ret.setExternalAttributes(source.getExternalAttributes());
        ret.setExtraFields(source.getExtraFields(true));
        ret.setCrc(source.getCrc());
        ret.setMethod(source.getMethod());
        ret.setSize(source.getSize());
        return ret;

		
	}
}
