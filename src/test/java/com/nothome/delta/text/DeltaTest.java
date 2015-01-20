package com.nothome.delta.text;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.junit.Test;

/**
 * The Class DeltaTest.
 */
public class DeltaTest {

    /**
     * For file.
     *
     * @param name the name
     * @return the reader
     * @throws FileNotFoundException the file not found exception
     */
    static Reader forFile(String name) throws FileNotFoundException {
        InputStreamReader isr = new InputStreamReader(DeltaTest.class.getResourceAsStream(name));
        return new BufferedReader(isr);
    }
    
    /**
     * Test ver.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Test
    public void testVer() throws IOException {
        test("/ver1.txt", "/ver2.txt");
        test("/ver2.txt", "/ver1.txt");
    }
    
    /**
     * Test lorem.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Test
    public void testLorem() throws IOException {
        test("/lorem.txt", "/lorem2.txt");
        test("/lorem2.txt", "/lorem.txt");
    }
    
    /**
     * Test lorem ver.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    @Test
    public void testLoremVer() throws IOException {
        test("/ver1.txt", "/lorem2.txt");
    }
    
    /**
     * Test.
     *
     * @param v1 the v1
     * @param v2 the v2
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void test(String v1, String v2) throws IOException {
        CharSequence string = Delta.toString(forFile(v1));
        CharSequence string2 = Delta.toString(forFile(v2));
        Delta d = new Delta();
        String delta = d.compute(string, string2);
        // System.err.println(delta);
        // System.err.println("----");
        String string3 = new TextPatcher(string).patch(delta);
        // System.out.println(string3);
        assertEquals(string2.toString(), string3);
    }
    
}
