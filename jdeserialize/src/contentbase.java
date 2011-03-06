package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * Provides a skeleton content implementation.
 */
public class contentbase implements content {
    public int handle;
    public boolean isExceptionObject;
    protected contenttype type;
    public contentbase(contenttype type) {
        this.type = type;
    }
    public boolean isExceptionObject() {
        return isExceptionObject;
    }
    public void setIsExceptionObject(boolean value) {
        isExceptionObject = value;
    }
    public contenttype getType() {
        return type;
    }
    public int getHandle() {
        return this.handle;
    }
    public void validate() throws ValidityException {
    }
}

