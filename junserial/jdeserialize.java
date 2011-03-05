import java.io.*;
import java.util.*;
import java.util.regex.*;

/*
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
 *     - better dumping of instances/content; make sure blockdata (e.g. ser10) gets dumped as well
 *     - test old jdk (particularly with old String instances)
 *     - For non-serializable classes, the number of fields is always zero. Neither the SC_SERIALIZABLE nor the SC_EXTERNALIZABLE flag bits are set. (error if fields > 0)
 *     - error if both serializable & externalizable flags are set
 *     - options
 *         - filter java.lang.* classes (on)
 *         - filter array classes (on)
 *         - handle inner classes according to inner classes spec rules (on)
 *         - output data from blockdata w/manifest
 *     - fixup local/anonymous classes (but how?)
 *     - handle val$ fields, local classes
 *     - figure out a way to represent field values?
 *     - split up classes
 *     - in documentation, note that classdesc can represent an instance of
 *       ObjectStreamClass
 */

public class jdeserialize {
    public static final String INDENT = "    ";
    public static final int CODEWIDTH = 90;
    public static final String linesep = System.getProperty("line.separator");

    private String filename;
    private Map<Integer,content> handles;
    private int curhandle;

    public static class ValidityException extends IOException {
        public ValidityException(String msg) {
            super(msg);
        }
    }
    public class ExceptionReadException extends IOException {
        public content exceptionobj;
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

    public static String indent(int level) {
        StringBuffer sb = new StringBuffer("");
        for(int i = 0; i < level; i++) {
            sb.append(INDENT);
        }
        return sb.toString();
    }

    public enum contenttype {
        INSTANCE, CLASS, ARRAY, STRING, ENUM, CLASSDESC, BLOCKDATA, EXCEPTIONSTATE
    }
    public interface content {
        public contenttype getType();
        public int getHandle();
        public void validate() throws ValidityException;
        public boolean isExceptionObject();
        public void setIsExceptionObject(boolean value);
    }
    public class exceptionstate extends contentbase {
        public content exceptionobj;
        public byte[] streamdata;
        public exceptionstate(content exobj, byte[] data) {
            super(contenttype.EXCEPTIONSTATE);
            this.exceptionobj = exobj;
            this.streamdata = data;
            this.handle = exobj.getHandle();
        }
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("[exceptionstate object " + exceptionobj.toString() + "  buflen " + streamdata.length);
            if(streamdata.length > 0) {
                for(int i = 0; i < streamdata.length; i++) {
                    if((i % 16) == 0) {
                        sb.append(linesep).append(String.format("%7x: ", Integer.valueOf(i)));
                    }
                    sb.append(" ").append(hexnoprefix(streamdata[i]));
                }
                sb.append(linesep);
            }
            sb.append("]");
            return sb.toString();
        }
    }
    public class enumobj extends contentbase {
        public classdesc classdesc;
        public stringobj value;
        public enumobj(int handle, classdesc cd, stringobj so) {
            super(contenttype.ENUM);
            this.handle = handle;
            this.classdesc = cd;
            this.value = so;
        }
        public String toString() {
            return "[enum " + hex(handle) + ": " + value.value + "]";
        }
    }
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
            return "[String " + hex(handle) + ": " + value + "]";
        }
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
                    throw new IOException("invalid byte in modified utf-8 string: " + hex(ba));
                }
            }
            this.value = sb.toString();
        }
    }
    public class classobj extends contentbase {
        public classdesc classdesc;
        public classobj(int handle, classdesc cd) {
            super(contenttype.CLASS);
            this.handle = handle;
            this.classdesc = cd;
        }
        public String toString() {
            return "[class " + hex(handle) + ": " + classdesc.toString() + "]";
        }
    }
    public class arraycoll extends ArrayList<Object> {
        private fieldtype ftype;
        public arraycoll(fieldtype ft) {
            super();
            this.ftype = ft;
        }
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("[arraycoll sz ").append(this.size());
            boolean first = true;
            for(Object o: this) {
                if(first) {
                    first = false;
                    sb.append(' ');
                } else {
                    sb.append(", ");
                }
                sb.append(o.toString());
            }
            return sb.toString();
        }
    }
    public class arrayobj extends contentbase {
        public classdesc classdesc;
        public arraycoll data;
        public arrayobj(int handle, classdesc cd, arraycoll data) {
            super(contenttype.ARRAY);
            this.handle = handle;
            this.classdesc = cd;
            this.data = data;
        }
        public String toString() {
            return "[array " + hex(handle) + " cd " + classdesc.toString() + ": " 
                + data.toString() + "]";
        }
    }
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
    public class blockdata extends contentbase {
        public byte[] buf;
        public blockdata(byte[] buf) {
            super(contenttype.BLOCKDATA);
            this.buf = buf;
        }
        public String toString() {
            return "[blockdata " + hex(handle) + ": " + buf.length + " bytes]";
        }
    }
    public class instance extends contentbase {
        public Map<classdesc, Map<field, Object>> fielddata;
        public Map<classdesc, List<content>> annotations;
        public classdesc classdesc;
        public instance() {
            super(contenttype.INSTANCE);
            this.fielddata = new HashMap<classdesc, Map<field, Object>>();
            this.annotations = new HashMap<classdesc, List<content>>();
        }
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("[instance " + hex(handle) + ": " + hex(classdesc.handle) + "/" + classdesc.name).append("]");
            return sb.toString();
        }
        public void readClassdata(DataInputStream dis) throws IOException {
            ArrayList<classdesc> classes = new ArrayList<classdesc>();
            classdesc.getHierarchy(classes);
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
            this.fielddata = alldata;
            this.annotations = annotations;
        }
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
                if(c.isExceptionObject()) {
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
        fieldtype(char ch) {
            this.ch = ch;
            this.javatype = null;
        }
        fieldtype(char ch, String javatype) {
            this.ch = ch;
            this.javatype = javatype;
        }
        public String getJavaType() {
            return this.javatype;
        }
        public char ch() { return ch; }
        public static fieldtype get(byte b) throws IOException {
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
                    throw new IOException("invalid field type char: " + b);
            }
        }
    }
    private int newHandle() {
        return curhandle++;
    }

    public class field {
        public fieldtype type;
        public String name; 
        public stringobj classname; 
        public boolean isInnerClassReference = false;
        public field(fieldtype type, String name, stringobj classname) throws ValidityException {
            this.type = type;
            this.name = name;
            this.classname = classname;
            if(classname != null) {
                validate(classname.value);
            }
        }
        public field(fieldtype type, String name) throws ValidityException {
            this(type, name, null);
        }
        /**
         * Get a string representing the type for this field in Java (the language)
         * format.
         * @returns a string representing the fully-qualified type of the field
         * @throws IOException if a validity or I/O error occurs
         */
        public String getJavaType() throws IOException {
            return resolveJavaType(this.type, this.classname == null ? null : this.classname.value, true);
        }
        /**
         * Changes the name of an object reference to the name specified.  This is used by
         * the inner-class-connection code to fix up field references.
         * @param newname the fully-qualified class 
         * @throws ValidityException if the field isn't a reference type, or another
         * validity error occurs
         */
        public void setReferenceTypeName(String newname) throws ValidityException {
            if(this.type != fieldtype.OBJECT) {
                throw new ValidityException("can't fix up a non-reference field!");
            }
            String nname = "L" + newname.replace('.', '/') + ";";
            this.classname.value = nname;
        }
        public void validate(String jt) throws ValidityException {
            if(this.type == fieldtype.OBJECT) {
                if(jt == null) {
                    throw new ValidityException("classname can't be null");
                }
                if(jt.charAt(0) != 'L') {
                    throw new ValidityException("invalid object field type descriptor: " + classname.value);
                }
                int end = jt.indexOf(';');
                if(end == -1 || end != (jt.length()-1)) {
                    throw new ValidityException("invalid object field type descriptor (must end with semicolon): " + classname.value);
                }
            }
        }
    }
    public String resolveJavaType(fieldtype type, String classname, boolean convertSlashes)  throws IOException {
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
            return type.javatype;
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
            if(c.isExceptionObject()) {
                throw new ExceptionReadException(c);
            }
            list.add(c);
        }
    }
    public void dump_Instance(int indentlevel, instance inst, PrintStream ps) {
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
        if(inst.annotations != null && inst.annotations.size() > 0) {
            sb.append(indent(1)).append("annotations: ").append(linesep);
            for(classdesc cd: inst.annotations.keySet()) {
                sb.append(indent(1)).append(cd.name).append(": ").append(linesep);
                List<content> cont = inst.annotations.get(cd);
                for(content c: cont) {
                    sb.append(indent(2)).append(c.toString()).append(linesep);
                }
            }
        }
        sb.append("]");
        ps.println(sb);
    }

    public void dump_ClassDesc(int indentlevel, classdesc cd, PrintStream ps) throws IOException {
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
            if(cd.isStaticMemberClass) {
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
                if(f.isInnerClassReference) {
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

    // note: this covers both normal and proxy class descriptors; check type!
    public enum classdesctype {
        NORMALCLASS, PROXYCLASS
    }
    public class classdesc extends contentbase {
        public classdesctype classtype;
        public String name;
        public long serialVersionUID;
        public byte descflags;
        public field[] fields;
        public List<classdesc> innerclasses;
        public List<content> annotations;
        public classdesc superclass;
        public String[] interfaces;
        public Set<String> enumconstants;
        public boolean isInnerClass = false;
        public boolean isLocalInnerClass = false;
        public boolean isStaticMemberClass = false;

        public classdesc(classdesctype classtype) {
            super(contenttype.CLASSDESC);
            this.classtype = classtype;
            this.enumconstants = new HashSet<String>();
            this.innerclasses = new ArrayList<classdesc>();
        }
        public void addInnerClass(classdesc cd) {
            innerclasses.add(cd);
        }
        public void addEnum(String constval) {
            this.enumconstants.add(constval);
        }
        /**
         * Determines whether this is an array type. 
         * @returns true if this is an array type.
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
            sb.append("[cd ").append(hex(handle)).append(": name ").append(name);
            sb.append(" uid ").append(serialVersionUID);
            sb.append("]");
            return sb.toString();
        }
        public void getHierarchy(ArrayList<classdesc> classes) {
            if(superclass != null) {
                if(superclass.classtype == classdesctype.PROXYCLASS) {
                    debugerr("warning: hit a proxy class in superclass hierarchy");
                } else {
                    superclass.getHierarchy(classes);
                }
            } 
            classes.add(this);
        }
        public void validate() throws ValidityException {
            if((descflags & ObjectStreamConstants.SC_ENUM) != 0) {
                // we're an enum; shouldn't have any fields/superinterfaces
                if(fields != null || interfaces != null) {
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
    // XXX: validate that it's a Throwable a la spec
    public content read_Exception(DataInputStream dis) throws IOException {
        debug("XXX ---------------------------------------------------");
        reset();
        byte tc = dis.readByte();
        if(tc == ObjectStreamConstants.TC_RESET) {
            throw new IOException("TC_RESET for object while reading exception: what should we do?");
        }
        content c = read_Content(tc, dis, false);
        if(c.isExceptionObject()) {
            throw new ExceptionReadException(c);
        }
        c.setIsExceptionObject(true);
        reset();
        return c;
    }

    // XXX: merge classDesc and newClassDesc.  Fine if the stream is well-formed, 
    // but, well....
    public classdesc read_newClassDesc(DataInputStream dis) throws IOException {
        byte tc = dis.readByte();
        classdesc cd = handle_newClassDesc(tc, dis);
        return cd;
    }
    public content read_prevObject(DataInputStream dis) throws IOException {
            int handle = dis.readInt();
            if(!handles.containsKey(Integer.valueOf(handle))) {
                debug("XXX handle table (looking for " + hex(handle) + "):");
                for(Integer o: handles.keySet()) {
                    debug("    XXX: " + hex(o));
                }
                throw new IOException("can't find an entry for handle " + hex(handle));
            }
            content c = handles.get(handle);
            debug("prevObject: handle " + hex(c.getHandle()) + " classdesc " + c.toString());
            return c;
    }

    public classdesc handle_newClassDesc(byte tc, DataInputStream dis) throws IOException {
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
            cd.superclass = read_newClassDesc(dis);
            setHandle(handle, cd);
            debug("read new classdesc: handle " + hex(handle) + " name " + name);
            return cd;
        } else if(tc == ObjectStreamConstants.TC_NULL) {
            debug("read null classdesc");
            return null;
        } else if(tc == ObjectStreamConstants.TC_REFERENCE) {
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
            cd.superclass = read_newClassDesc(dis);
            setHandle(handle, cd);
            cd.name = "(proxy class; no name)";
            debug("read new proxy classdesc: handle " + hex(handle) + " names [" + Arrays.toString(interfaces) + "]");
            return cd;
        } else {
            throw new IOException("expected TC_CLASSDESC or TC_PROXYCLASSDESC, got " + hex(tc));
        }
    }
    public arrayobj read_newArray(DataInputStream dis) throws IOException {
        classdesc cd = read_newClassDesc(dis);
        int handle = newHandle();       // XXX set
        debug("reading new array: handle " + hex(handle) + " classdesc " + cd.toString());
        if(cd.name.length() < 2) {
            throw new IOException("invalid name in array classdesc: " + cd.name);
        }
        arraycoll ac = read_arrayValues(cd.name.substring(1), dis);
        return new arrayobj(handle, cd, ac);
    }
    public arraycoll read_arrayValues(String str, DataInputStream dis) throws IOException {
        byte b = str.getBytes("UTF-8")[0];      // XXX: redecoding sucks.
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
        classdesc cd = read_newClassDesc(dis);
        int handle = newHandle();
        debug("reading new class: handle " + hex(handle) + " classdesc " + cd.toString());
        classobj c = new classobj(handle, cd);
        setHandle(handle, c);
        return c;
    }
    public enumobj read_newEnum(DataInputStream dis) throws IOException {
        classdesc cd = read_newClassDesc(dis);
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
            data = new byte[0];
            // XXX: can this even happen?  it shouldn't in sun ObjectOutputStream.. 
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
        classdesc cd = read_newClassDesc(dis);
        int handle = newHandle();
        debug("reading new object: handle " + hex(handle) + " classdesc " + cd.toString());
        instance i = new instance();
        i.classdesc = cd;
        i.handle = handle;
        setHandle(handle, i);
        i.readClassdata(dis);
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
     * @returns an object representing the last read item from the stream 
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
                if(c.isExceptionObject()) {
                    c = new exceptionstate(c, lis.getRecordedData());
                }
                content.add(c);
            }
            debug("");
            connectMemberClasses();
            debug("XXX done reading.  content:");
            for(content c: content) {
                debug("" + c);
            }

            debug("");
            debug("XXXX classes:");
            for(content c: handles.values()) {
                if(c instanceof classdesc) {
                    classdesc cl = (classdesc)c;
                    // XXX: make this an option
                    if(!cl.isStaticMemberClass && !cl.isInnerClass && (!cl.isArrayClass() || cl.isArrayClass())) {
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
     * Connect member classes according to the rules specified by the JDK 1.1 Inner
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
                cd.isLocalInnerClass = islocal;
                cd.isInnerClass = true;
                f.isInnerClassReference = true;
                newnames.put(cd, inner);
            }
        }
        for(classdesc cd: classes.values()) {
            if(cd.classtype == classdesctype.PROXYCLASS) {
                continue;
            }
            if(cd.isInnerClass) {
                continue;
            }
            Matcher clmat = clpat.matcher(cd.name);
            if(!clmat.matches()) {
                continue;
            }
            String outer = clmat.group(1), inner = clmat.group(2);
            classdesc outercd = classes.get(outer);
            if(outercd == null) {
                throw new ValidityException("couldn't connect static member class: outer class not found for class name " + cd.name);
            }
            outercd.addInnerClass(cd);
            cd.isStaticMemberClass = true;
            newnames.put(cd, inner);
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
        if(value < 0) {
            value = 256 + value;
        }
        String s = Long.toString(value, 16);
        if(s.length() == 1) {
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
        if(args.length < 1) {
            debugerr("args: file1 [file2 .. fileN]");
            System.exit(1);
        }
        for(String filename: args) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(filename);
                jdeserialize jd = new jdeserialize(filename);
                jd.run(fis);
            } catch(EOFException eoe) {
                debugerr("EOF error while attempting to decode file " + filename + ": " + eoe.getMessage());
                eoe.printStackTrace();      // XXX
            } catch(IOException ioe) {
                debugerr("error while attempting to decode file " + filename + ": " + ioe.getMessage());
                ioe.printStackTrace();     // XXX
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
