package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * Exception used to signal that an exception object was successfully read from the 
 * stream.  This object holds a reference to the serialized exception object.
 */
public class ExceptionReadException extends IOException {
    public static final long serialVersionUID = 2277356908919221L;
    public content exceptionobj;
    /**
     * Constructor.
     * @param c the serialized exception object that was read
     */
    public ExceptionReadException(content c) {
        super("serialized exception read during stream");
        this.exceptionobj = c;
    }
    /**
     * Gets the Exception object that was thrown.
     * @returns the content representing the serialized exception object
     */
    public content getExceptionObject() {
        return exceptionobj;
    }
}

