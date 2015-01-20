/**
 * This package contains classes for creating patches for binary files output
 * in the GDIFF format.
 * <p>
 * The patch creation class is {@link com.nothome.delta.Delta}.
 * <p>
 * The patch applier class is {@link com.nothome.delta.GDiffPatcher}.
 * <p>
 * Example use:
 <pre>
 byte source[] = ...;
 byte target[] = ...;
 Delta d = new Delta();
 byte patch[] = d.compute(source, target);
 
 GDiffPatcher p = new GDiffPatcher();
 byte patchedSource[] = p.patch(source, patch);
 
 assert java.util.Arrays.equals(target, patchedSource);
 </pre>
 *
 * @see com.nothome.delta.Delta
 * @see com.nothome.delta.GDiffPatcher
 */
package com.nothome.delta;