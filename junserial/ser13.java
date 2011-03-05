import java.io.*;
import java.util.*;
import java.lang.reflect.*;

public class ser13 {
	public static void main(String args[]) {
		do_write();
	}

	public static void do_write() {
		try {
            blobproxy bp = new blobproxy(55);
            Class<?> pclass = Proxy.getProxyClass(blobproxy.class.getClassLoader(), 
                    new Class[] { List.class, Iterator.class });
            Constructor<?> constructor = pclass.getConstructor(new Class<?>[] { InvocationHandler.class });
            List s = (List)constructor.newInstance(new Object[] { bp });
            System.out.println("s.get: " + s.get(2));
            Iterator it = (Iterator)s;
            System.out.println("it.next: " + it.next());

			FileOutputStream fos = new FileOutputStream("ser13.duh");
			ModObjectOutputStream oos = new ModObjectOutputStream(fos);
            try {
                oos.writeObject(bp);
                oos.writeObject(pclass);
                oos.writeObject(s);
                oos.writeObject(it);
                oos.writeObject(it.next());
            } catch (IOException ignore) {
            }
            oos.writeObject("klsadfj lkasdf lkadsfkl kdsfalklj fof course");
			oos.flush();
		} catch(FileNotFoundException e) {
			System.out.println("file not found");
			System.exit(1);
		} catch(IOException e) {
			System.out.println("IOException");
			System.exit(1);
		} catch (Throwable t) {
            t.printStackTrace();
        }
		System.out.println("wrote");
	}

	public static void do_read() {
		try {
			FileInputStream fos = new FileInputStream("ser13.duh");
			ObjectInputStream ois = new ObjectInputStream(fos);
            blobex bl = (blobex)ois.readObject();
			System.out.println("blobex: " + bl.toString());
		} catch(FileNotFoundException e) {
			System.out.println("file not found");
			System.exit(1);
		} catch(IOException e) {
			System.out.println("IOException");
			System.exit(1);
		} catch(ClassNotFoundException e) {
			System.out.println("ClassNotFoundException");
			System.exit(1);
		}
	}
}

