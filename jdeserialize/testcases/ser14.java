import java.io.*;
import java.util.*;
import java.lang.reflect.*;

public class ser14 {
	public static void main(String args[]) {
		do_write();
        do_read();
	}

	public static void do_write() {
		try {
			FileOutputStream fos = new FileOutputStream("ser14.duh");
			ObjectOutputStream oos = new ObjectOutputStream(fos);
            ArrayList<String> strl = new ArrayList<String>();
            strl.add("str1");
            strl.add("str2");
            strl.add("str3");
            ArrayList<Integer> stri = new ArrayList<Integer>();
            stri.add(new Integer(5));
            stri.add(new Integer(6));
            stri.add(new Integer(7));
            oos.writeObject(strl);
            oos.writeObject(stri);
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
			FileInputStream fos = new FileInputStream("ser14.duh");
			ObjectInputStream ois = new ObjectInputStream(fos);
            ArrayList<String> istrl = (ArrayList<String>)ois.readObject();
            System.out.println("istrl:");
            for(String s: istrl) {
                System.out.println("    " + s);
            }
            ArrayList<Integer> istri = (ArrayList<Integer>)ois.readObject();
            System.out.println("istri:");
            for(Integer i: istri) {
                System.out.println("    " + i);
            }
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

