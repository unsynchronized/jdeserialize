package org.unsynchronized;
import java.io.*;

/**
 * Represents an opaque block of data written to the stream.  Primarily, these are used to
 * write class and object annotations by ObjectOutputStream overrides; they can also occur
 * inside an object, when the object overrides Serializable.writeObject().  Their
 * interpretation is hereby left to users.
 */
public class blockdata extends contentbase {
    /**
     * The block data read from the stream.
     */
    public byte[] buf;

    /**
     * Constructor.
     *
     * @param buf the block data
     */
    public blockdata(byte[] buf) {
        super(contenttype.BLOCKDATA);
        this.buf = buf;
    }
    public String toString() {
        return "[blockdata " + jdeserialize.hex(handle) + ": " + buf.length + " bytes]";
    }
}
