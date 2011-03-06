package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * <p>
 * Enum class that describes the type of a field encoded inside a classdesc description.
 * </p>
 *
 * <p>
 * This stores both information on the type (reference/array vs. primitive) and, in cases
 * of reference or array types, the name of the class being referred to.  
 * </p>
 */
public enum fieldtype {
    BYTE ('B', "byte"),
    CHAR ('C', "char"),
    DOUBLE ('D', "double"), 
    FLOAT ('F', "float"),
    INTEGER ('I', "int"),
    LONG ('J', "long"),
    SHORT ('S', "String"),
    BOOLEAN ('Z', "boolean"),
    ARRAY ('['),
    OBJECT ('L');
    private final char ch;
    private final String javatype;

    /**
     * Constructor for non-object (primitive) types.
     *
     * @param ch the character representing the type (must match one of those listed in
     * prim_typecode or obj_typecode in the Object Serialization Stream Protocol)
     */
    fieldtype(char ch) {
        this(ch, null);
    }

    /**
     * Constructor.
     *
     * @param ch the character representing the type (must match one of those listed in
     * prim_typecode or obj_typecode in the Object Serialization Stream Protocol)
     * @param javatype the name of the object class, where applicable (or null if not)
     */
    fieldtype(char ch, String javatype) {
        this.ch = ch;
        this.javatype = javatype;
    }

    /**
     * Gets the class name for a reference or array type.
     *
     * @return the name of the class being referred to, or null if this is not a
     * reference/array type
     */
    public String getJavaType() {
        return this.javatype;
    }

    /**
     * Gets the type character for this field.
     *
     * @return the type code character for this field; values will be one of those in
     * prim_typecode or obj_typecode in the protocol spec
     */
    public char ch() { return ch; }

    /**
     * Given a byte containing a type code, return the corresponding enum.
     *
     * @param b the type code; must be one of the charcaters in obj_typecode or
     * prim_typecode in the protocol spec
     * @return the corresponding fieldtype enum
     * @throws ValidityException if the type code is invalid
     */
    public static fieldtype get(byte b) throws ValidityException {
        switch(b) {
            case 'B': 
                return BYTE;
            case 'C':
                return CHAR;
            case 'D':
                return DOUBLE;
            case 'F':
                return FLOAT;
            case 'I':
                return INTEGER;
            case 'J':
                return LONG;
            case 'S':
                return SHORT;
            case 'Z':
                return BOOLEAN;
            case '[':
                return ARRAY;
            case 'L':
                return OBJECT;
            default:
                throw new ValidityException("invalid field type char: " + b);
        }
    }
}

