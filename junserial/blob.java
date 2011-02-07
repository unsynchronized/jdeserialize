import java.util.*;
import java.io.*;

public class blob implements Serializable {
    public class inner implements Serializable {
        private int ia;
        private String ib;
        public inner(int ia, String ib) {
            this.ia = ia;
            this.ib = ib;
        }
        public String toString() {
            return "[inner ia " + ia + "  ib " + ib + "]";
        }
    }
    private int a;
    private String b;
    private inner i;

    public String toString() {
        return "[blob a " + a + "  b " + b + "  i " + i.toString() + "]";
    }

    public blob(int a, String b) {
        this.a = a;
        this.b = b;
        this.i = new inner(a+1, b);
    }
}
