import java.io.*;
import java.util.Date;

public class ser5 {
	public static void main(String args[]) {
		do_write();
		do_read();
	}

	public static void do_write() {
		try {
            blob2 bl = new blob2(1234, "abcd");
			FileOutputStream fos = new FileOutputStream("ser5.duh");
			ObjectOutputStream oos = new ObjectOutputStream(fos);
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
			FileInputStream fos = new FileInputStream("ser5.duh");
			ObjectInputStream ois = new ObjectInputStream(fos);
            blob2 bl = (blob2)ois.readObject();
			System.out.println("blob2: " + bl.toString());
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

