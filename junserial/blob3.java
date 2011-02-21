import java.util.*;
import java.io.*;

public class blob3 implements Serializable {
    private int[][] amdi;
    private int[] ai;
    private String[] foo;
    private int ix = 0x12345678;

    public String toString() {
        return "[blob3]";
    }

    public blob3(int a) {
        this.amdi = new int[10][3];
        for(int i = 0; i < 10; i++) {
            for(int j = 0; j < 3; j++) {
                amdi[i][j] = a+i+j;
            }
        }
        this.ai = new int[] { a+1, a+2, a+3, a+4, a+5, };
        this.foo = new String[] { "one", "two", "three", "four" };
    }
}
