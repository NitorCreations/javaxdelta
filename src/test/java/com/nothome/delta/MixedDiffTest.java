package com.nothome.delta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.junit.Test;

import at.spardat.xma.xdelta.test.JarDeltaJarPatcherTest;

/**
 * Tests random bytes.
 * 
 * Code from {@link JarDeltaJarPatcherTest}.
 */
public class MixedDiffTest {

    /** The random. */
    private SecureRandom random;

    /** The byte max length. */
    private int byteMaxLength = 1000;

    /**
     * Instantiates a new mixed diff test.
     */
    public MixedDiffTest() {
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test it.
     *
     * @throws Exception the exception
     */
    @Test
    public void testIt() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int j = 0; j < 10; j++) {
            byte[] bytes = getRandomBytes();
            out.write(bytes, 0, bytes.length);
        }

        byte[] oldBytes = out.toByteArray();
        byte[] newBytes = getRandomBytes();
        byte[] mixedBytes = mixBytes(oldBytes, newBytes);
        
        Delta d = new Delta();
        ByteArrayOutputStream patch = new ByteArrayOutputStream();
        d.compute(oldBytes, mixedBytes, patch);
        GDiffPatcher p = new GDiffPatcher();
        byte[] madeOld = p.patch(oldBytes, patch.toByteArray());
        assertEquals(mixedBytes.length, madeOld.length);
        for (int i = 0; i < mixedBytes.length; i++) {
            byte a = mixedBytes[i];
            byte b = madeOld[i];
            if (a != b)
                fail(a + " " + b + " " + i);
        }
    }

    /**
     * Gets the random bytes.
     *
     * @return the random bytes
     */
    private byte[] getRandomBytes() {
        int lengt = randomSize(byteMaxLength);
        byte[] bytes = new byte[lengt];
        random.nextBytes(bytes);
        return bytes;
    }

    /**
     * Random size.
     *
     * @param maxSize the max size
     * @return the int
     */
    private int randomSize(int maxSize) {
        return ((int) (maxSize * random.nextFloat())) + 1;
    }

    /**
     * Mix bytes.
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
        System.arraycopy(newBytes, 0, bytes, oldBytes.length / 2 - 1,
                newBytes.length / 2);
        System.arraycopy(oldBytes, oldBytes.length / 2, bytes, oldBytes.length
                / 2 + newBytes.length / 2 - 1, oldBytes.length / 2);
        System.arraycopy(newBytes, newBytes.length / 2, bytes, oldBytes.length
                + newBytes.length / 2 - 1, newBytes.length / 2);

        return bytes;
    }

}
