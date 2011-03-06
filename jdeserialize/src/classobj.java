package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * This represents a Class object (i.e. an instance of type Class) serialized in the
 * stream.
 */
public class classobj extends contentbase {
    /**
     * The class description, including its name.
     */
    public classdesc classdesc;

    /**
     * Constructor.
     *
     * @param handle the instance's handle
     * @param cd the instance's class description
     */
    public classobj(int handle, classdesc cd) {
        super(contenttype.CLASS);
        this.handle = handle;
        this.classdesc = cd;
    }
    public String toString() {
        return "[class " + jdeserialize.hex(handle) + ": " + classdesc.toString() + "]";
    }
}

