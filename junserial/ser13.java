import java.io.*;
import java.util.*;
import java.lang.reflect.*;

public class ser13 {
	public static void main(String args[]) {
		do_write();
	}

	public static void do_write() {
		try {
            blob5 b5 = new blob5(9989);

			FileOutputStream fos = new FileOutputStream("ser13.duh");
			ObjectOutputStream oos = new ObjectOutputStream(fos);
            try {
                oos.writeObject(b5.getinner());
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

