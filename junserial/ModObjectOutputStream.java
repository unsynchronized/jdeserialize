import java.util.*;
import java.io.*;
import java.lang.annotation.*;

public class ModObjectOutputStream extends ObjectOutputStream {
    public ModObjectOutputStream(OutputStream out) throws IOException {
        super(out);
    }
    public void annotateClass(Class<?> cl) throws IOException {
        this.writeObject("hello from ModObjectOutputStream: writing class " + cl.getName());
        this.writeInt(312);
    }
    public void annotateProxyClass(Class<?> cl) throws IOException {
        this.writeObject("hello from ModObjectOutputStream: writing proxy class " + cl.getName());
        this.writeInt(708);
    }
}
