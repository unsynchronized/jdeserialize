import java.io.*;
import java.util.Date;

public class deser {
	public static void main(String args[]) {
		do_read();
	}

	public static void do_read() {
		try {
			FileInputStream fos = new FileInputStream("serialize.duh");
			ObjectInputStream ois = new ObjectInputStream(fos);
			String s = (String)ois.readObject();
			Date d = (Date)ois.readObject();
            Object o = ois.readObject();
			System.out.println("string: *" + s + "*");
			System.out.println("date: " + d.toString());
			System.out.println("blob: " + o.toString());
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

