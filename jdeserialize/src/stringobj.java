package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * Represents a serialized string object.  This is primarily used in serialized streams;
 * however, it is also used internally by other objects, since string objects have
 * handles as well.
 */
public class stringobj extends contentbase {
    public String value;
    private int readorthrow(ByteArrayInputStream bais) throws EOFException {
        int x = bais.read();
        if(x == -1) {
            throw new EOFException("unexpected eof in modified utf-8 string");
        }
        return x;
    }
    public String toString() {
        return "[String " + jdeserialize.hex(handle) + ": \"" + value + "\"]";
    }
    /**
     * Constructor.
     *
     * @param handle the string object's handle
     * @param data the bytes corresponding to the string 
     * @throws IOException if an I/O or validity error occurs
     */
    public stringobj(int handle, byte[] data) throws IOException {
        super(contenttype.STRING);
        this.handle = handle;
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        StringBuffer sb = new StringBuffer();
        while(true) {
            int ba = bais.read();
            if(ba == -1) {
                break;
            }
            if((ba & 0x80) == 0) {                  /* U+0001..U+007F */
                if(ba == 0) {
                    throw new IOException("improperly-encoded null in modified UTF8 string!");
                }
                sb.append((char)ba);
            } else if((ba & 0xf0) == 0xe0) {        /* U+0800..U+FFFF */
                int bb = readorthrow(bais);
                if((bb & 0xc0) != 0x80) {
                    throw new IOException("byte b in 0800-FFFF seq doesn't begin with correct prefix");
                }
                int bc = readorthrow(bais);
                if((bc & 0xc0) != 0x80) {
                    throw new IOException("byte c in 0800-FFFF seq doesn't begin with correct prefix");
                }
                int cp = 
                    ((ba & 0xf) << 12)
                    | ((bb & 0x3f) << 6)
                    | (bc & 0x3f);
                sb.append((char)cp);
            } else if((ba & 0xe0) == 0xc0) {        /* U+0080..U+07FF */
                int bb = readorthrow(bais);
                if((bb & 0xc0) != 0x80) {
                    throw new IOException("byte b in 0080-07FF seq doesn't begin with correct prefix");
                }
                int cp = ((ba & 0x1f) << 6) | (bb & 0x3f);
                sb.append((char)cp);
            } else {
                throw new IOException("invalid byte in modified utf-8 string: " + jdeserialize.hex(ba));
            }
        }
        this.value = sb.toString();
    }
}
