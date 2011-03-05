package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * Represents an array instance, including the values the comprise the array.  
 *
 * Note that in arrays of primitives, the classdesc will be named "[x", where x is the
 * field type code representing the primitive type.  See jdeserialize.resolveJavaType()
 * for an example of analysis/generation of human-readable names from these class names.
 */
public class arrayobj extends contentbase {
    /**
     * Type of the array instance.
     */
    public classdesc classdesc;

    /**
     * Values of the array, in the order they were read from the stream.
     */
    public arraycoll data;

    public arrayobj(int handle, classdesc cd, arraycoll data) {
        super(contenttype.ARRAY);
        this.handle = handle;
        this.classdesc = cd;
        this.data = data;
    }
    public String toString() {
        return "[array " + jdeserialize.hex(handle) + " classdesc " + classdesc.toString() + ": " 
            + data.toString() + "]";
    }
}

