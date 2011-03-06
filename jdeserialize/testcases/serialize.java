import java.io.*;
import java.util.Date;

public class serialize {
	public static void main(String args[]) {
		do_write();
		do_read();
	}

	public static void do_write() {
		try {
            blob bl = new blob(1234, "abcd");
			FileOutputStream fos = new FileOutputStream("serialize.duh");
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject("Today");
			oos.writeObject(new Date());
            oos.writeObject(bl);
			oos.flush();
		} catch(FileNotFoundException e) {
			System.out.println("file not found");
			System.exit(1);
		} catch(IOException e) {
			System.out.println("IOException");
			System.exit(1);
		}
		System.out.println("wrote");
	}

	public static void do_read() {
		try {
			FileInputStream fos = new FileInputStream("serialize.duh");
			ObjectInputStream ois = new ObjectInputStream(fos);
			String s = (String)ois.readObject();
			Date d = (Date)ois.readObject();
            blob bl = (blob)ois.readObject();
			System.out.println("string: *" + s + "*");
			System.out.println("date: " + d.toString());
			System.out.println("blob: " + bl.toString());
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

