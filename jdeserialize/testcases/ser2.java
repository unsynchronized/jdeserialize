import java.io.*;
import java.util.Date;

public class ser2 {
    public static class blob implements Serializable {
        private String s;
        public blob(String s) {
            this.s = s;
        }
        public String toString() {
            return("[blob: " + s + "]");
        }
    }
	public static void main(String args[]) {
		do_write();
		do_read();
	}

	public static void do_write() {
		try {
            blob bl = new blob("abcd");
			FileOutputStream fos = new FileOutputStream("serialize3.duh");
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
			FileInputStream fos = new FileInputStream("serialize3.duh");
			ObjectInputStream ois = new ObjectInputStream(fos);
            blob bl = (blob)ois.readObject();
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

