import java.io.*;
import java.util.Date;

public class ser8 {
	public static void main(String args[]) {
		do_write();
		do_read();
	}

	public static void do_write() {
		try {
            blob4 bl = new blob4(1234);
			FileOutputStream fos = new FileOutputStream("ser8.duh");
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
			FileInputStream fos = new FileInputStream("ser8.duh");
			ObjectInputStream ois = new ObjectInputStream(fos);
            blob4 bl = (blob4)ois.readObject();
			System.out.println("blob4: " + bl.toString());
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

