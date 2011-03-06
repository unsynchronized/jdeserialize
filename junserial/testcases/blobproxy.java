import java.util.*;
import java.lang.reflect.*;
import java.io.*;

public class blobproxy implements Serializable, InvocationHandler {
    public Object invoke(Object proxy, Method method, Object[] args) {
        System.out.println("invoke: proxy " + proxy.getClass().toString()
                + "  method " + method.toString());
        return "foo";
    }

     public int a;
     public String b;

    public String toString() {
        return "[blobproxy a " + a + "  b " + b + "]";
    }

    public blobproxy(int a) {
        String b = "zoo";
        this.a = a;
        this.b = b;
    }

    public static void main(String[] args) {
        try {
            blobproxy bp = new blobproxy(55);
            Class<?> pclass = Proxy.getProxyClass(blobproxy.class.getClassLoader(), 
                    new Class[] { List.class, Iterator.class });
            Constructor<?> constructor = pclass.getConstructor(new Class<?>[] { InvocationHandler.class });
            List s = (List)constructor.newInstance(new Object[] { bp });
            System.out.println("s.get: " + s.get(2));
            Iterator it = (Iterator)s;
            System.out.println("it.next: " + it.next());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
