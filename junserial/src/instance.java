package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * Represents an instance of a non-enum, non-Class, non-ObjectStreamClass, 
 * non-array class, including the non-transient field values, for all classes in its
 * hierarchy and inner classes.
 */
public class instance extends contentbase {
    /**
     * Collection of field data, organized by class description.  
     */
    public Map<classdesc, Map<field, Object>> fielddata;

    /**
     * Class description for this instance.
     */
    public classdesc classdesc;

    /**
     * Constructor.
     */
    public instance() {
        super(contenttype.INSTANCE);
        this.fielddata = new HashMap<classdesc, Map<field, Object>>();
    }
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(classdesc.name).append(' ').append("_h").append(jdeserialize.hex(handle))
            .append(" = r_").append(jdeserialize.hex(classdesc.handle)).append(";  ");
        //sb.append("// [instance " + jdeserialize.hex(handle) + ": " + jdeserialize.hex(classdesc.handle) + "/" + classdesc.name).append("]");
        return sb.toString();
    }
}
