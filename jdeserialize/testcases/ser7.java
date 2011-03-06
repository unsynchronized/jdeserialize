import java.io.*;
import java.util.Date;

public class ser7 {
	public static void main(String args[]) {
		do_write();
		do_read();
	}

	public static void do_write() {
		try {
            blob3 bl = new blob3(1234);
			FileOutputStream fos = new FileOutputStream("ser7.duh");
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
			FileInputStream fos = new FileInputStream("ser7.duh");
			ObjectInputStream ois = new ObjectInputStream(fos);
            blob3 bl = (blob3)ois.readObject();
			System.out.println("blob3: " + bl.toString());
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

