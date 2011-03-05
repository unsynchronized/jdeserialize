package org.unsynchronized;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 *
 * References:
 *     - Java Object Serialization Specification ch. 6 (Object Serialization Stream
 *       Protocol):
 *       http://download.oracle.com/javase/6/docs/platform/serialization/spec/protocol.html
 *     - "Modified UTF-8 Strings" within the JNI specification: 
 *       http://download.oracle.com/javase/1.5.0/docs/guide/jni/spec/types.html#wp16542
 *     - "Inner Classes Specification" within the JDK 1.1.8 docs:
 *       http://java.sun.com/products/archive/jdk/1.1/
 *
 * XXX TODO: 
 *     - better dumping of instances/content
 *     - normalize method names
 *     - options
 *         - textual dump of all content/classes (on)
 *         - filter java.lang.* classes (on)
 *         - filter array classes (on)
 *         - handle inner classes according to inner classes spec rules (on)
 *         - filter val$this fields (off)
 *         - output data from blockdata w/manifest (off)
 *         - filter out non-Java characters from all classes (on)
 *     - in documentation, note that classdesc can represent an instance of
 *       ObjectStreamClass
 *     - test old jdk (particularly with old String instances)
 */

public class jdeserialize {
    public static final long serialVersionUID = 78790714646095L;
    public static final String INDENT = "    ";
    public static final int CODEWIDTH = 90;
    public static final String linesep = System.getProperty("line.separator");

    private String filename;
    private Map<Integer,content> handles;
    private int curhandle;

    /**
     * Suitably escapes non-printable-ASCII characters (and doublequotes) for use 
     * in a Java string literal.
     *
     * @param str string to escape
     * @return an escaped version of the string
     */
    public static String unicodeEscape(String str) {
        StringBuffer sb = new StringBuffer();
        int cplen = str.codePointCount(0, str.length());
        for(int i = 0; i < cplen; i++) {
            int cp = str.codePointAt(i);
            if(cp == '"') {
                sb.append("\\\"");
            }
            if(cp < 0x20 || cp > 0x7f) {
                sb.append("\\u" + hexnoprefix(4));
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    public static String indent(int level) {
        StringBuffer sb = new StringBuffer("");
        for(int i = 0; i < level; i++) {
            sb.append(INDENT);
        }
        return sb.toString();
    }

    public void read_Classdata(DataInputStream dis, instance inst) throws IOException {
        ArrayList<classdesc> classes = new ArrayList<classdesc>();
        inst.classdesc.getHierarchy(classes);
        Map<classdesc, Map<field, Object>> alldata = new HashMap<classdesc, Map<field, Object>>();
        Map<classdesc, List<content>> ann = new HashMap<classdesc, List<content>>();
        for(classdesc cd: classes) {
            Map<field, Object> values = new HashMap<field, Object>();
            if((cd.descflags & ObjectStreamConstants.SC_SERIALIZABLE) != 0) {
                if((cd.descflags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0) {
                    throw new IOException("SC_EXTERNALIZABLE & SC_SERIALIZABLE encountered");
                }
                for(field f: cd.fields) {
                    Object o = read_FieldValue(f.type, dis);
                    values.put(f, o);
                }
                alldata.put(cd, values);
                if((cd.descflags & ObjectStreamConstants.SC_WRITE_METHOD) != 0) {
                    if((cd.descflags & ObjectStreamConstants.SC_ENUM) != 0) {
                        throw new IOException("SC_ENUM & SC_WRITE_METHOD encountered!");
                    }
                    ann.put(cd, read_classAnnotation(dis));
                }
            } else if((cd.descflags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0) {
                if((cd.descflags & ObjectStreamConstants.SC_SERIALIZABLE) != 0) {
                    throw new IOException("SC_SERIALIZABLE & SC_EXTERNALIZABLE encountered");
                }
                if((cd.descflags & ObjectStreamConstants.SC_BLOCK_DATA) != 0) {
                    throw new EOFException("hit externalizable with nonzero SC_BLOCK_DATA; can't interpret data");
                } else {
                    ann.put(cd, read_classAnnotation(dis));
                }
            }
        }
        inst.fielddata = alldata;
    }

    public Object read_FieldValue(fieldtype f, DataInputStream dis) throws IOException {
        switch(f) {
            case BYTE:
                return Byte.valueOf(dis.readByte());
            case CHAR:
                return Character.valueOf(dis.readChar());
            case DOUBLE:
                return Double.valueOf(dis.readDouble());
            case FLOAT:
                return Float.valueOf(dis.readFloat());
            case INTEGER:
                return Integer.valueOf(dis.readInt());
            case LONG:
                return Long.valueOf(dis.readLong());
            case SHORT:
                return Short.valueOf(dis.readShort());
            case BOOLEAN:
                return Boolean.valueOf(dis.readBoolean());
            case OBJECT:
            case ARRAY:
                byte stc = dis.readByte();
                if(f == fieldtype.ARRAY && stc != ObjectStreamConstants.TC_ARRAY) {
                    throw new IOException("array type listed, but typecode is not TC_ARRAY: " + hex(stc));
                }
                content c = read_Content(stc, dis, false);
                if(c != null && c.isExceptionObject()) {
                    throw new ExceptionReadException(c);
                }
                return c;
            default:
                throw new IOException("can't process type: " + f.toString());
        }
    }
    public jdeserialize(String filename) {
        this.filename = filename;
    }
    private int newHandle() {
        return curhandle++;
    }

    public static String resolveJavaType(fieldtype type, String classname, boolean convertSlashes)  throws IOException {
        if(type == fieldtype.ARRAY) {
            StringBuffer asb = new StringBuffer("");
            for(int i = 0; i < classname.length(); i++) {
                char ch = classname.charAt(i);
                switch(ch) {
                    case '[':
                        asb.append("[]");
                        continue;
                    case 'L':
                        return decodeClassName(classname.substring(i), convertSlashes) + asb.toString();
                    default:
                        if(ch < 1 || ch > 127) {
                            throw new ValidityException("invalid array field type descriptor character: " + classname);
                        }
                        fieldtype ft = fieldtype.get((byte)ch);
                        if(i != (classname.length()-1)) {
                            throw new ValidityException("array field type descriptor is too long: " + classname);
                        }
                        return ft.getJavaType() + asb.toString();
                }
            }
            throw new ValidityException("array field type descriptor is too short: " + classname);
        } else if(type == fieldtype.OBJECT) {
            return decodeClassName(classname, convertSlashes);
        } else {
            return type.getJavaType();
        }
    }

    public List<content> read_classAnnotation(DataInputStream dis) throws IOException {
        List<content> list = new ArrayList<content>();
        while(true) {
            byte tc = dis.readByte();
            if(tc == ObjectStreamConstants.TC_ENDBLOCKDATA) {
                return list;
            }
            if(tc == ObjectStreamConstants.TC_RESET) {
                reset();
                continue;
            }
            content c = read_Content(tc, dis, true);
            if(c != null && c.isExceptionObject()) {
                throw new ExceptionReadException(c);
            }
            list.add(c);
        }
    }
    public static void dump_Instance(int indentlevel, instance inst, PrintStream ps) {
        StringBuffer sb = new StringBuffer();
        sb.append("[instance " + hex(inst.handle) + ": " + hex(inst.classdesc.handle) + "/" + inst.classdesc.name);
        if(inst.fielddata != null && inst.fielddata.size() > 0) {
            sb.append(linesep).append("  field data:").append(linesep);
            for(classdesc cd: inst.fielddata.keySet()) {
                sb.append("    ").append(hex(cd.handle)).append("/").append(cd.name).append(":").append(linesep);
                for(field f: inst.fielddata.get(cd).keySet()) {
                    Object o = inst.fielddata.get(cd).get(f);
                    sb.append("        ").append(f.name).append(": ");
                    if(o instanceof content) {
                        content c = (content)o;
                        int h = c.getHandle();
                        if(h == inst.handle) {
                            sb.append("this");
                        } else {
                            sb.append("r" + hex(h));
                        }
                        sb.append(": ").append(c.toString());
                        sb.append(linesep);
                    } else {
                        sb.append("" + o).append(linesep);
                    }
                }
            }
        }
// XXX: remove?
//        if(inst.annotations != null && inst.annotations.size() > 0) {
//            sb.append(indent(1)).append("annotations: ").append(linesep);
//            for(classdesc cd: inst.annotations.keySet()) {
//                sb.append(indent(1)).append(cd.name).append(": ").append(linesep);
//                List<content> cont = inst.annotations.get(cd);
//                for(content c: cont) {
//                    sb.append(indent(2)).append(c.toString()).append(linesep);
//                }
//            }
//        }
        sb.append("]");
        ps.println(sb);
    }

    public static void dump_ClassDesc(int indentlevel, classdesc cd, PrintStream ps) throws IOException {
        if(cd.annotations != null && cd.annotations.size() > 0) {
            ps.println(indent(indentlevel) + "// annotations: ");
            for(content c: cd.annotations) {
                ps.print(indent(indentlevel) + "// " + indent(1));
                ps.println(c.toString());
            }
        }
        if(cd.classtype == classdesctype.NORMALCLASS) {
            if((cd.descflags & ObjectStreamConstants.SC_ENUM) != 0) {
                ps.print(indent(indentlevel) + "enum " + cd.name + " {");
                boolean shouldindent = true;
                int len = indent(indentlevel+1).length();
                for(String econst: cd.enumconstants) {
                    if(shouldindent) {
                        ps.println("");
                        ps.print(indent(indentlevel+1));
                        shouldindent = false;
                    }
                    len += econst.length();
                    ps.print(econst + ", ");
                    if(len >= CODEWIDTH) {
                        len = indent(indentlevel+1).length();
                        shouldindent = true;
                    }
                }
                ps.println("");
                ps.println(indent(indentlevel) + "}");
                return;
            } 
            ps.print(indent(indentlevel));
            if(cd.isStaticMemberClass()) {
                ps.print("static ");
            }
            ps.print("class " + (cd.name.charAt(0) == '[' ? resolveJavaType(fieldtype.ARRAY, cd.name, false) : cd.name));
            if(cd.superclass != null) {
                ps.print(" extends " + cd.superclass.name);
            }
            ps.print(" implements ");
            if((cd.descflags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0) {
                ps.print("java.io.Externalizable");
            } else {
                ps.print("java.io.Serializable");
            }
            if(cd.interfaces != null) {
                for(String intf: cd.interfaces) {
                    ps.print(", " + intf);
                }
            }
            ps.println(" {");
            for(field f: cd.fields) {
                if(f.isInnerClassReference()) {
                    continue;
                }
                ps.print(indent(indentlevel+1) + f.getJavaType());
                ps.println(" " + f.name + ";");
            }
            for(classdesc icd: cd.innerclasses) {
                dump_ClassDesc(indentlevel+1, icd, ps);
            }
            ps.println(indent(indentlevel)+"}");
        } else if(cd.classtype == classdesctype.PROXYCLASS) {
            ps.print(indent(indentlevel) + "// proxy class " + hex(cd.handle));
            if(cd.superclass != null) {
                ps.print(" extends " + cd.superclass.name);
            }
            ps.println(" implements ");
            for(String intf: cd.interfaces) {
                ps.println(indent(indentlevel) + "//    " + intf + ", ");
            }
            if((cd.descflags & ObjectStreamConstants.SC_EXTERNALIZABLE) != 0) {
                ps.println(indent(indentlevel) + "//    java.io.Externalizable");
            } else {
                ps.println(indent(indentlevel) + "//    java.io.Serializable");
            }
        } else {
            throw new ValidityException("encountered invalid classdesc type!");
        }
    }

    public void setHandle(int handle, content c) throws IOException {
        if(handles.containsKey(handle)) {
            throw new IOException("trying to reset handle " + hex(handle));
        }
        handles.put(handle, c);
    }
    public void reset() {
        debug("reset ordered!");
        handles.clear();
        curhandle = ObjectStreamConstants.baseWireHandle;  // 0x7e0000
    }
    /**
     * Read the content of a thrown exception object.  According to the spec, this must be
     * an object of type Throwable.  Although the Sun JDK always appears to provide enough
     * information about the hierarchy to reach all the way back to java.lang.Throwable,
     * it's unclear whether this is actually a requirement.  From my reading, it's
     * possible that some other ObjectOutputStream implementations may leave some gaps in
     * the hierarchy, forcing this app to hit the classloader.  To avoid this, we merely
     * ensure that the written object is indeed an instance; ensuring that the object is
     * indeed a Throwable is an exercise left to the user.
     */
    public content read_Exception(DataInputStream dis) throws IOException {
        reset();
        byte tc = dis.readByte();
        if(tc == ObjectStreamConstants.TC_RESET) {
            throw new ValidityException("TC_RESET for object while reading exception: what should we do?");
        }
        content c = read_Content(tc, dis, false);
        if(c == null) {
            throw new ValidityException("stream signaled for an exception, but exception object was null!");
        }
        if(!(c instanceof instance)) { 
            throw new ValidityException("stream signaled for an exception, but content is not an object!");
        }
        if(c.isExceptionObject()) {
            throw new ExceptionReadException(c);
        }
        c.setIsExceptionObject(true);
        reset();
        return c;
    }

    public classdesc read_classDesc(DataInputStream dis) throws IOException {
        byte tc = dis.readByte();
        classdesc cd = handle_classDesc(tc, dis, false);
        return cd;
    }
    public classdesc read_newClassDesc(DataInputStream dis) throws IOException {
        byte tc = dis.readByte();
        classdesc cd = handle_newClassDesc(tc, dis);
        return cd;
    }
    public content read_prevObject(DataInputStream dis) throws IOException {
            int handle = dis.readInt();
            if(!handles.containsKey(Integer.valueOf(handle))) {
                throw new ValidityException("can't find an entry for handle " + hex(handle));
            }
            content c = handles.get(handle);
            debug("prevObject: handle " + hex(c.getHandle()) + " classdesc " + c.toString());
            return c;
    }

    public classdesc handle_newClassDesc(byte tc, DataInputStream dis) throws IOException {
        return handle_classDesc(tc, dis, true);
    }
    public classdesc handle_classDesc(byte tc, DataInputStream dis, boolean mustBeNew) throws IOException {
        if(tc == ObjectStreamConstants.TC_CLASSDESC) {
            String name = dis.readUTF();
            long serialVersionUID = dis.readLong();
            int handle = newHandle();
            byte descflags = dis.readByte();
            short nfields = dis.readShort();
            if(nfields < 0) {
                throw new IOException("invalid field count: " + nfields);
            }
            field[] fields = new field[nfields];
            for(short s = 0; s < nfields; s++) {
                byte ftype = dis.readByte();
                if(ftype == 'B' || ftype == 'C' || ftype == 'D' 
                        || ftype == 'F' || ftype == 'I' || ftype == 'J'
                        || ftype == 'S' || ftype == 'Z') {
                    String fieldname = dis.readUTF();
                    fields[s] = new field(fieldtype.get(ftype), fieldname);
                } else if(ftype == '[' || ftype == 'L') {
                    String fieldname = dis.readUTF();
                    byte stc = dis.readByte();
                    stringobj classname = read_newString(stc, dis);
                    //String classname = dis.readUTF();
                    fields[s] = new field(fieldtype.get(ftype), fieldname, classname);
                } else {
                    throw new IOException("invalid field type char: " + hex(ftype));
                }
            }
            classdesc cd = new classdesc(classdesctype.NORMALCLASS);
            cd.name = name;
            cd.serialVersionUID = serialVersionUID;
            cd.handle = handle;
            cd.descflags = descflags;
            cd.fields = fields;
            cd.annotations = read_classAnnotation(dis);
            cd.superclass = read_classDesc(dis);
            setHandle(handle, cd);
            debug("read new classdesc: handle " + hex(handle) + " name " + name);
            return cd;
        } else if(tc == ObjectStreamConstants.TC_NULL) {
            if(mustBeNew) {
                throw new ValidityException("expected new class description -- got null!");
            }
            debug("read null classdesc");
            return null;
        } else if(tc == ObjectStreamConstants.TC_REFERENCE) {
            if(mustBeNew) {
                throw new ValidityException("expected new class description -- got a reference!");
            }
            content c = read_prevObject(dis);
            if(!(c instanceof classdesc)) {
                throw new IOException("referenced object not a class description!");
            }
            classdesc cd = (classdesc)c;
            return cd;
        } else if(tc == ObjectStreamConstants.TC_PROXYCLASSDESC) {
            int handle = newHandle();
            int icount = dis.readInt();
            if(icount < 0) {
                throw new IOException("invalid proxy interface count: " + hex(icount));
            }
            String interfaces[] = new String[icount];
            for(int i = 0; i < icount; i++) {
                interfaces[i] = dis.readUTF();
            }
            classdesc cd = new classdesc(classdesctype.PROXYCLASS);
            cd.handle = handle;
            cd.interfaces = interfaces;
            cd.annotations = read_classAnnotation(dis);
            cd.superclass = read_classDesc(dis);
            setHandle(handle, cd);
            cd.name = "(proxy class; no name)";
            debug("read new proxy classdesc: handle " + hex(handle) + " names [" + Arrays.toString(interfaces) + "]");
            return cd;
        } else {
            throw new ValidityException("expected a valid class description starter got " + hex(tc));
        }
    }
    public arrayobj read_newArray(DataInputStream dis) throws IOException {
        classdesc cd = read_classDesc(dis);
        int handle = newHandle();
        debug("reading new array: handle " + hex(handle) + " classdesc " + cd.toString());
        if(cd.name.length() < 2) {
            throw new IOException("invalid name in array classdesc: " + cd.name);
        }
        arraycoll ac = read_arrayValues(cd.name.substring(1), dis);
        return new arrayobj(handle, cd, ac);
    }
    public arraycoll read_arrayValues(String str, DataInputStream dis) throws IOException {
        byte b = str.getBytes("UTF-8")[0];
        fieldtype ft = fieldtype.get(b);
        int size = dis.readInt();
        if(size < 0) {
            throw new IOException("invalid array size: " + size);
        }

        arraycoll ac = new arraycoll(ft);
        for(int i = 0; i < size; i++) {
            ac.add(read_FieldValue(ft, dis));
            continue;
        }
        return ac;
    }
    public classobj read_newClass(DataInputStream dis) throws IOException {
        classdesc cd = read_classDesc(dis);
        int handle = newHandle();
        debug("reading new class: handle " + hex(handle) + " classdesc " + cd.toString());
        classobj c = new classobj(handle, cd);
        setHandle(handle, c);
        return c;
    }
    public enumobj read_newEnum(DataInputStream dis) throws IOException {
        classdesc cd = read_classDesc(dis);
        if(cd == null) {
            throw new IOException("enum classdesc can't be null!");
        }
        int handle = newHandle();
        debug("reading new enum: handle " + hex(handle) + " classdesc " + cd.toString());
        byte tc = dis.readByte();
        stringobj so = read_newString(tc, dis);
        cd.addEnum(so.value);
        setHandle(handle, so);
        return new enumobj(handle, cd, so);
    }
    public stringobj read_newString(byte tc, DataInputStream dis) throws IOException {
        byte[] data;
        if(tc == ObjectStreamConstants.TC_REFERENCE) {
                content c = read_prevObject(dis);
                if(!(c instanceof stringobj)) {
                    throw new IOException("got reference for a string, but referenced value was something else!");
                }
                return (stringobj)c;
        }
        int handle = newHandle();
        if(tc == ObjectStreamConstants.TC_STRING) {
            int len = dis.readUnsignedShort();
            data = new byte[len];
        } else if(tc == ObjectStreamConstants.TC_LONGSTRING) {
            long len = dis.readLong();
            if(len < 0) {
                throw new IOException("invalid long string length: " + len);
            }
            if(len > 2147483647) {
                throw new IOException("long string is too long: " + len);
            }
            if(len < 65536) {
                debugerr("warning: small string length encoded as TC_LONGSTRING: " + len);
            }
            data = new byte[(int)len];
        } else if(tc == ObjectStreamConstants.TC_NULL) {
            throw new ValidityException("stream signaled TC_NULL when string type expected!");
        } else {
            throw new IOException("invalid tc byte in string: " + hex(tc));
        }
        dis.readFully(data);
        debug("reading new string: handle " + hex(handle) + " bufsz " + data.length);
        stringobj sobj = new stringobj(handle, data);
        setHandle(handle, sobj);
        return sobj;
    }
    public blockdata read_blockdata(byte tc, DataInputStream dis) throws IOException {
        int size;
        if(tc == ObjectStreamConstants.TC_BLOCKDATA) {
            size = dis.readUnsignedByte();
        } else if(tc == ObjectStreamConstants.TC_BLOCKDATALONG) {
            size = dis.readInt();
        } else {
            throw new IOException("invalid tc value for blockdata: " + hex(tc));
        }
        if(size < 0) {
            throw new IOException("invalid value for blockdata size: " + size);
        }
        byte[] b = new byte[size];
        dis.readFully(b);
        debug("read blockdata of size " + size);
        return new blockdata(b);
    }
    public instance read_newObject(DataInputStream dis) throws IOException {
        classdesc cd = read_classDesc(dis);
        int handle = newHandle();
        debug("reading new object: handle " + hex(handle) + " classdesc " + cd.toString());
        instance i = new instance();
        i.classdesc = cd;
        i.handle = handle;
        setHandle(handle, i);
        read_Classdata(dis, i);
        debug("done reading object for handle " + hex(handle));
        return i;
    }

    /**
     * Read the next object corresponding to the spec grammar rule "content", and return
     * an object of type content.
     *
     * By and large, there is a 1:1 mapping of content items and returned instances.  The
     * one case where this isn't true is when an exception is embedded inside another
     * object.  When this is encountered, only the serialized exception object is
     * returned; it's up to the caller to backtrack in order to gather any data from the
     * object that was being serialized when the exception was thrown.
     *
     * @param tc the last byte read from the stream; it must be one of the TC_* values
     * within ObjectStreamConstants.*
     * @param dis the DataInputStream to read from
     * @param blockdata whether or not to read TC_BLOCKDATA (this is the difference
     * between spec rules "object" and "content").
     * @return an object representing the last read item from the stream 
     * @throws IOException when a validity or I/O error occurs while reading
     */
    public content read_Content(byte tc, DataInputStream dis, boolean blockdata) throws IOException {
        try {
            switch(tc) {
                case ObjectStreamConstants.TC_OBJECT:
                    return read_newObject(dis);
                case ObjectStreamConstants.TC_CLASS:
                    return read_newClass(dis);
                case ObjectStreamConstants.TC_ARRAY:
                    return read_newArray(dis);
                case ObjectStreamConstants.TC_STRING:
                case ObjectStreamConstants.TC_LONGSTRING:
                    return read_newString(tc, dis);
                case ObjectStreamConstants.TC_ENUM:
                    return read_newEnum(dis);
                case ObjectStreamConstants.TC_CLASSDESC:
                case ObjectStreamConstants.TC_PROXYCLASSDESC:
                    return handle_newClassDesc(tc, dis);
                case ObjectStreamConstants.TC_REFERENCE:
                    return read_prevObject(dis);
                case ObjectStreamConstants.TC_NULL:
                    return null;
                case ObjectStreamConstants.TC_EXCEPTION:
                    return read_Exception(dis);
                case ObjectStreamConstants.TC_BLOCKDATA:
                case ObjectStreamConstants.TC_BLOCKDATALONG:
                    if(blockdata == false) {
                        throw new IOException("got a blockdata TC_*, but not allowed here: " + hex(tc));
                    }
                    return read_blockdata(tc, dis);
                default:
                    throw new IOException("unknown content tc byte in stream: " + hex(tc));
            }
        } catch (ExceptionReadException ere) {
            return ere.getExceptionObject();
        }
    }

    public void run(InputStream is) throws IOException {
        LoggerInputStream lis = null;
        DataInputStream dis = null;
        try {
            lis = new LoggerInputStream(is);
            dis = new DataInputStream(lis);
            System.out.println("version 1: " + ObjectStreamConstants.PROTOCOL_VERSION_1);
            System.out.println("version 2: " + ObjectStreamConstants.PROTOCOL_VERSION_2);

            short magic = dis.readShort();
            if(magic != ObjectStreamConstants.STREAM_MAGIC) {
                throw new IOException("file magic mismatch!  expected " + ObjectStreamConstants.STREAM_MAGIC + ", got " + magic);
            }
            short streamversion = dis.readShort();
            if(streamversion != ObjectStreamConstants.STREAM_VERSION) {
                throw new IOException("file version mismatch!  expected " + ObjectStreamConstants.STREAM_VERSION + ", got " + streamversion);
            }
            handles = new HashMap<Integer,content>();
            curhandle = ObjectStreamConstants.baseWireHandle;  // 0x7e0000
            ArrayList<content> content = new ArrayList<content>();
            while(true) {
                byte tc;
                try { 
                    lis.record();
                    tc = dis.readByte();
                    if(tc == ObjectStreamConstants.TC_RESET) {
                        reset();
                        continue;
                    }
                } catch (EOFException eoe) {
                    break;
                }
                content c = read_Content(tc, dis, true);
                if(c != null && c.isExceptionObject()) {
                    c = new exceptionstate(c, lis.getRecordedData());
                }
                content.add(c);
            }
            debug("");
            debug("XXX validating");
            for(content c: handles.values()) {
                c.validate();
            }
            connectMemberClasses();

            debug("XXX validating");
            for(content c: handles.values()) {
                c.validate();
            }
            debug("XXX content:");
            for(content c: content) {
                debug("" + c);
            }

            debug("");
            debug("XXXX classes:");
            for(content c: handles.values()) {
                if(c instanceof classdesc) {
                    classdesc cl = (classdesc)c;
                    if(!cl.isStaticMemberClass() && !cl.isInnerClass() && (!cl.isArrayClass() || cl.isArrayClass())) {
                        dump_ClassDesc(0, cl, System.out);
                        debug("");
                    }
                }
            }
            debug("XXXX instances:");
            for(content c: handles.values()) {
                if(c instanceof instance) {
                    instance i = (instance)c;
                    dump_Instance(0, i, System.out);
                }
            }
        } finally {
            if(dis != null) {
                try {
                    dis.close();
                } catch (Exception ignore) { }
            }
            if(lis != null) {
                try {
                    lis.close();
                } catch (Exception ignore) {}
            }
        }
    }

    /**
     * Connects member classes according to the rules specified by the JDK 1.1 Inner
     * Classes Specification.  
     *
     * Inner classes:
     * for each class C containing an object reference member R named this$N, do:
     *     if the name of C matches the pattern O$I
     *     AND the name O matches the name of an existing type T
     *     AND T is the exact type referred to by R, then:
     *         don't display the declaration of R in normal dumping,
     *         consider C to be an inner class of O named I
     *
     * Static member classes (after):
     * for each class C matching the pattern O$I, 
     * where O is the name of a class in the same package
     * AND C is not an inner class according to the above algorithm:
     *     consider C to be an inner class of O named I
     *
     * This functions fills in the isInnerClass value in classdesc, the
     * isInnerClassReference value in field, the isLocalInnerClass value in 
     * classdesc, and the isStaticMemberClass value in classdesc where necessary.
     *
     * A word on static classes: serializing a static member class S doesn't inherently
     * require serialization of its parent class P.  Unlike inner classes, S doesn't
     * retain an instance of P, and therefore P's class description doesn't need to be
     * written.  In these cases, if parent classes can be found, their static member
     * classes will be connected; but if they can't be found, the names will not be
     * changed and no ValidityException will be thrown.
     *
     * @throws ValidityException if the found values don't correspond to spec
     */
    public void connectMemberClasses() throws IOException {
        HashMap<classdesc, String> newnames = new HashMap<classdesc, String>();
        HashMap<String, classdesc> classes = new HashMap<String, classdesc>();
        HashSet<String> classnames = new HashSet<String>();
        for(content c: handles.values()) {
            if(!(c instanceof classdesc)) {
                continue;
            }
            classdesc cd = (classdesc)c;
            classes.put(cd.name, cd);
            classnames.add(cd.name);
        }
        Pattern fpat = Pattern.compile("^this\\$(\\d+)$");
        Pattern clpat = Pattern.compile("^((?:[^\\$]+\\$)*[^\\$]+)\\$([^\\$]+)$");
        for(classdesc cd: classes.values()) {
            if(cd.classtype == classdesctype.PROXYCLASS) {
                continue;
            }
            for(field f: cd.fields) {
                if(f.type != fieldtype.OBJECT) {
                    continue;
                }
                Matcher m = fpat.matcher(f.name);
                if(!m.matches()) {
                    continue;
                }
                boolean islocal = false;
                Matcher clmat = clpat.matcher(cd.name);
                if(!clmat.matches()) {
                    throw new ValidityException("inner class enclosing-class reference field exists, but class name doesn't match expected pattern: class " + cd.name + " field " + f.name);
                }
                String outer = clmat.group(1), inner = clmat.group(2);
                classdesc outercd = classes.get(outer);
                if(outercd == null) {
                    throw new ValidityException("couldn't connect inner classes: outer class not found for field name " + f.name);
                }
                if(!outercd.name.equals(f.getJavaType())) {
                    throw new ValidityException("outer class field type doesn't match field type name: " + f.classname.value + " outer class name " + outercd.name);
                }
                outercd.addInnerClass(cd);
                cd.setIsLocalInnerClass(islocal);
                cd.setIsInnerClass(true);
                f.setIsInnerClassReference(true);
                newnames.put(cd, inner);
            }
        }
        for(classdesc cd: classes.values()) {
            if(cd.classtype == classdesctype.PROXYCLASS) {
                continue;
            }
            if(cd.isInnerClass()) {
                continue;
            }
            Matcher clmat = clpat.matcher(cd.name);
            if(!clmat.matches()) {
                continue;
            }
            String outer = clmat.group(1), inner = clmat.group(2);
            classdesc outercd = classes.get(outer);
            if(outercd != null) {
                outercd.addInnerClass(cd);
                cd.setIsStaticMemberClass(true);
                newnames.put(cd, inner);
            }
        }
        for(classdesc ncd: newnames.keySet()) {
            String newname = newnames.get(ncd);
            if(classnames.contains(newname)) {
                throw new ValidityException("can't rename class from " + ncd.name + " to " + newname + " -- class already exists!");
            }
            for(classdesc cd: classes.values()) {
                if(cd.classtype == classdesctype.PROXYCLASS) {
                    continue;
                }
                for(field f: cd.fields) {
                    if(f.getJavaType().equals(ncd.name)) {
                        f.setReferenceTypeName(newname);
                    }
                }
            }
            if(classnames.remove(ncd.name) == false) {
                throw new ValidityException("tried to remove " + ncd.name + " from classnames cache, but couldn't find it!");
            }
            ncd.name = newname;
            if(classnames.add(newname) == false) {
                throw new ValidityException("can't rename class to " + newname + " -- class already exists!");
            }
        }
    }

    /**
     * Decodes a class name according to the field-descriptor format in the jvm spec,
     * section 4.3.2.
     * @param fdesc name in field-descriptor format (Lfoo/bar/baz;)
     * @param convertSlashes true iff slashes should be replaced with periods (true for
     * "real" field-descriptor format; false for names in classdesc)
     * @return a fully-qualified class name
     * @throws ValidityException if the name isn't valid
     */
    public static String decodeClassName(String fdesc, boolean convertSlashes) throws ValidityException {
        if(fdesc.charAt(0) != 'L' || fdesc.charAt(fdesc.length()-1) != ';' || fdesc.length() < 3) {
            throw new ValidityException("invalid name (not in field-descriptor format): " + fdesc);
        }
        String subs = fdesc.substring(1, fdesc.length()-1);
        if(convertSlashes) {
            return subs.replace('/', '.');
        } 
        return subs;
    }

    public static String hexnoprefix(long value) {
        return hexnoprefix(value, 2);
    }
    public static String hexnoprefix(long value, int len) {
        if(value < 0) {
            value = 256 + value;
        }
        String s = Long.toString(value, 16);
        while(s.length() < len) {
            s = "0" + s;
        }
        return s;
    }
    public static String hex(long value) {
        return "0x" + hexnoprefix(value);
    }
    public static void debugerr(String message) {
        System.err.println(message);
    }
    public static void debug(String message) {
        System.out.println(message);
    }

    public static void main(String[] args) {
        HashMap<String, Integer> options = new HashMap<String, Integer>();
        Getopt go = new Getopt();
        go.addOption("-help", 0, "Show this list.");
        go.addOption("-debug", 0, "Write debug info generated during parsing to stdout.");
        go.addOption("-javalang", 0, "Filter out java.lang.* classes from class output.");
        go.addOption("-nocontent", 0, "Don't output textual descriptions of instances.");
        go.addOption("-noarray", 0, "Don't filter array classes from class declarations.");
        go.addOption("-noinner", 0, "Don't attempt connect inner classes to their enclosing classes.");
        go.addOption("-filternames", 0, "In class names, replace illegal Java identifier characters with legal ones.");
        go.addOption("-noclasses", 0, "Don't output class declarations.");
        go.addOption("-blockdata", 1, "Write raw blockdata out to the specified file.");
        go.addOption("-blockdatamanifest", 1, "Write blockdata manifest out to the specified file.");
        try {
            go.parse(args);
        } catch (Getopt.OptionParseException ope) {
            System.err.println("argument error: " + ope.getMessage());
            System.out.println(go.getDescriptionString());
            System.exit(1);
        }
        if(go.hasOption("-help")) {
            System.out.println(go.getDescriptionString());
            System.exit(1);
        }
        List<String> fargs = go.getOtherArguments();
        if(fargs.size() < 1) {
            debugerr("args: [options] file1 [file2 .. fileN]");
            System.exit(1);
        }
        for(String filename: fargs) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(filename);
                jdeserialize jd = new jdeserialize(filename);
                jd.run(fis);
            } catch(EOFException eoe) {
                debugerr("EOF error while attempting to decode file " + filename + ": " + eoe.getMessage());
                eoe.printStackTrace();
            } catch(IOException ioe) {
                debugerr("error while attempting to decode file " + filename + ": " + ioe.getMessage());
                ioe.printStackTrace();
            } finally {
                if(fis != null) {
                    try {
                        fis.close();
                    } catch (Exception ignore) { }
                }
            }
        }
    }
}
