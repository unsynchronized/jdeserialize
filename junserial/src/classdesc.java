package org.unsynchronized;
import java.io.*;
import java.util.*;

/**
 * Represents the entire serialized prototype of the class, including all fields,
 * inner classes, class annotations, and inheritance hierarchy.  This includes proxy class
 * descriptions.
 *
 * Generally, this class is used to represent the type of an instance written to an
 * ObjectOutputStream with its writeObject() method, or of a related array or field type.
 * However, there's a notable exception: when instances of type java.io.ObjectStreamClass
 * are written with writeObject(), only their class description is written (cf. Object
 * Serialization Specification, 4.3).  They will be represented with an instance of
 * classdesc as well.
 */
public class classdesc extends contentbase {
    /**
     * Type of the class being represented; either a normal class or a proxy class.
     */
    public classdesctype classtype;

    /**
     * Class name.
     */
    public String name;

    /**
     * Serial version UID, as recorded in the stream.
     */
    public long serialVersionUID;

    /**
     * Description flags byte; this should be a mask of values from the ObjectStreamContants 
     * class.  Refer to chapter 6 of the Object Stream Serialization Protocol for details.
     */
    public byte descflags;

    /**
     * Array of fields in the class, in the order serialized by the stream writer.
     */
    public field[] fields;

    /**
     * List of inner classes, in the order serialized by the stream writer.
     */
    public List<classdesc> innerclasses;
    
    /**
     * List of annotation objects; these are *not* Java annotations, but data written by
     * the annotateClass(Class<?>) and annotateProxyClass(Class<?>) methods of an
     * ObjectOutputStream.  
     */
    public List<content> annotations;

    /**
     * The superclass of the object, if available.
     */
    public classdesc superclass;

    /**
     * Array of serialized interfaces, in the order serialized by the stream writer.
     */
    public String[] interfaces;

    /**
     * Set of enum constants, for enum classes.
     */
    public Set<String> enumconstants;

    private boolean isInnerClass = false;
    /**
     * True if this class has been determined to be an inner class; this determination is
     * generally made by connectMemberClasses().
     * 
     * @return true if the class is an inner class
     */
    public boolean isInnerClass() {
        return isInnerClass;
    }
    /**
     * Sets the value that denotes that the class is an inner class.
     *
     * @param nis the value to set
     */
    public void setIsInnerClass(boolean nis) {
        this.isInnerClass = nis;
    }

    private boolean isLocalInnerClass = false;
    /**
     * True if this class has been determined to be a local inner class; this
     * determination is generally made by connectMemberClasses().
     *
     * @return true if the class is a local inner class
     */
    public boolean isLocalInnerClass() {
        return isLocalInnerClass;
    }
    /**
     * Sets the flag that denotes whether this class is a local inner class.
     * 
     * @param nis the value to set
     */
    public void setIsLocalInnerClass(boolean nis) {
        this.isLocalInnerClass = nis;
    }

    private boolean isStaticMemberClass = false;
    /**
     * True if this class has been determined to be a static member class; this
     * determination is generally made by connectMemberClasses().
     *
     * Note that in some cases, static member classes' descriptions will be serialized
     * even though their enclosing class is not.  In these cases, this may return false.
     * See connectMemberClasses() for details.
     *
     * @return true if this is a static member class
     */
    public boolean isStaticMemberClass() {
        return isStaticMemberClass;
    }
    /**
     * Sets the flag that denotes whether this class is a static member class.
     *
     * @param nis the value to set
     */
    public void setIsStaticMemberClass(boolean nis) {
        this.isStaticMemberClass = nis;
    }

    /**
     * Constructor.
     *
     * @param classtype the type of the class
     */
    public classdesc(classdesctype classtype) {
        super(contenttype.CLASSDESC);
        this.classtype = classtype;
        this.enumconstants = new HashSet<String>();
        this.innerclasses = new ArrayList<classdesc>();
    }

    /**
     * Add an inner class to the description's list.
     * @param cd inner class to add
     */
    public void addInnerClass(classdesc cd) {
        innerclasses.add(cd);
    }

    /**
     * Add an enum constant to the description's set.
     *
     * @param constval enum constant string
     */
    public void addEnum(String constval) {
        this.enumconstants.add(constval);
    }

    /**
     * Determines whether this is an array type. 
     * @return true if this is an array type.
     */
    public boolean isArrayClass() {
        if(name != null && name.length() > 1 && name.charAt(0) == '[') {
            return true;
        } else {
            return false;
        }
    }
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("[cd ").append(jdeserialize.hex(handle)).append(": name ").append(name);
        sb.append(" uid ").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }

    /**
     * Generates a list of all class descriptions in this class's hierarchy, in the order
     * described by the Object Stream Serialization Protocol.  This is the order in which
     * fields are read from the stream.
     * 
     * @return a list of all serialized superclasses
     */
    public void getHierarchy(ArrayList<classdesc> classes) {
        if(superclass != null) {
            if(superclass.classtype == classdesctype.PROXYCLASS) {
                jdeserialize.debugerr("warning: hit a proxy class in superclass hierarchy");
            } else {
                superclass.getHierarchy(classes);
            }
        } 
        classes.add(this);
    }
    public void validate() throws ValidityException {
        // If neither SC_SERIALIZABLE nor SC_EXTERNALIZABLE is set, then the number of
        // fields is always zero.  (spec section 4.3)
        if((descflags & (ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_EXTERNALIZABLE)) == 0 && fields != null && fields.length > 0) {
            throw new ValidityException("non-serializable, non-externalizable class has fields!");
        }
        if((descflags & (ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_EXTERNALIZABLE)) == (ObjectStreamConstants.SC_SERIALIZABLE | ObjectStreamConstants.SC_EXTERNALIZABLE)) {
            throw new ValidityException("both Serializable and Externalizable are set!");
        }
        if((descflags & ObjectStreamConstants.SC_ENUM) != 0) {
            // we're an enum; shouldn't have any fields/superinterfaces
            if((fields != null && fields.length > 0) || interfaces != null) {
                throw new ValidityException("enums shouldn't implement interfaces or have non-constant fields!");
            }
        } else {
            // non-enums shouldn't have enum constant fields.  
            if(enumconstants != null && enumconstants.size() > 0) {
                throw new ValidityException("non-enum classes shouldn't have enum constants!");
            }
        }
    }
}

