package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * Generic interface for all data that may be read from the stream (except null).  
 *
 * A successful read of the stream will result in a series of content instances or null
 * references.  For details on specific metadata, see documentation on implementing
 * classes/subinterfaces.
 */
public interface content {
    /**
     * @return the type of instance represented by this object.
     */
    public contenttype getType();

    /**
     * Get the numeric handle by which this object was referred to in the object stream.
     * These handles are used internally by Object{Output,Input}Stream as a mechanism to
     * avoid costly duplication.
     *
     * CAUTION: they are *not* necessarily unique across all objects in a given stream!
     * If an exception was thrown during serialization (which is most likely to happen
     * during a serialized objct's writeObject() implementation), then the stream resets
     * before and after the exception is serialized.  
     *
     * @return the handle assigned in the stream
     */
    public int getHandle();

    /**
     * Performs extra object-specific validity checks.  
     *
     * @throws ValidityException if the object's state is invalid
     */
    public void validate() throws ValidityException;

    /**
     * Tells whether or not this object is an exception that was caught during
     * serialization.  
     *
     * Note:  Not every Throwable or Exception in the stream will have this flag set to
     * true; only those which were thrown <i>during serialization</i> will
     * 
     * @return true iff the object was an exception thrown during serialization
     */
    public boolean isExceptionObject();

    /**
     * Sets the flag that tells whether or not this object is an exception that was caught
     * during serialization.
     *
     * @param value the new value to use
     */
    public void setIsExceptionObject(boolean value);
}

