package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * Represents an enum instance.  As noted in the serialization spec, this consists of
 * merely the class description (represented by a classdesc) and the string corresponding
 * to the enum's value.  No other fields are ever serialized.
 */
public class enumobj extends contentbase {
    /**
     * The enum's class description.
     */
    public classdesc classdesc;

    /**
     * The string that represents the enum's value.
     */
    public stringobj value;

    /**
     * Constructor.
     *
     * @param handle the enum's handle
     * @param cd the enum's class description
     * @param so the enum's value
     */
    public enumobj(int handle, classdesc cd, stringobj so) {
        super(contenttype.ENUM);
        this.handle = handle;
        this.classdesc = cd;
        this.value = so;
    }
    public String toString() {
        return "[enum " + jdeserialize.hex(handle) + ": " + value.value + "]";
    }
}
