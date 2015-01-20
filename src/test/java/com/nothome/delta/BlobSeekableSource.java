package com.nothome.delta;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.sql.Blob;
import java.sql.SQLException;

/**
 * The Class BlobSeekableSource.
 */
public class BlobSeekableSource implements SeekableSource {

    /** The lob. */
    private Blob lob;
    
    /** The pos. */
    private long pos = 0;
    
    /** The is. */
    private InputStream is;

    /**
     * Constructs a new BlobSeekableSource.
     *
     * @param lob the lob
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public BlobSeekableSource(Blob lob) throws IOException {
        this.lob = lob;
        this.is = getStream();
    }

    /* (non-Javadoc)
     * @see java.io.Closeable#close()
     */
    public void close() throws IOException {
        is.close();
    }
    
    /**
     * Gets the stream.
     *
     * @return the stream
     * @throws IOException Signals that an I/O exception has occurred.
     */
    InputStream getStream() throws IOException {
        try {
            return lob.getBinaryStream();
        } catch (SQLException e) {
            throw (IOException)(new IOException().initCause(e));
        }
    }

    /**
     * Length.
     *
     * @return the long
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public long length() throws IOException {
        try {
            return lob.length();
        } catch (SQLException e) {
            throw (IOException)(new IOException().initCause(e));
        }
    }

    /**
     * Read.
     *
     * @param b the b
     * @param off the off
     * @param len the len
     * @return the int
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public int read(byte[] b, int off, int len) throws IOException {
        if (is != null)
            return is.read(b, off, len);
        try {
            byte[] read = lob.getBytes(pos, len);
            pos += read.length;
            System.arraycopy(read, 0, b, off, len);
            return read.length;
        } catch (SQLException e) {
            throw (IOException)(new IOException().initCause(e));
        }
    }

    /* (non-Javadoc)
     * @see com.nothome.delta.SeekableSource#seek(long)
     */
    public void seek(long pos) throws IOException {
        if (pos == 0) {
            is = getStream();
        } else {
            is.close();
            is = null;
        }
        this.pos = pos;
    }

    /* (non-Javadoc)
     * @see com.nothome.delta.SeekableSource#read(java.nio.ByteBuffer)
     */
    public int read(ByteBuffer bb) throws IOException {
        return 0;
    }
}
