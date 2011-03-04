package bluh.zuh;

import java.util.*;
import java.io.*;


public class blob4 implements Serializable {
    public static class istatic implements Serializable {
        private int isa;
        private String isb;
        public istatic(int isa, String isb) {
            this.isa = isa;
            this.isb = isb;
        }
    }
    private class inner implements Serializable {
        private int ia;
        private String ib;
        private inner2 ii; 
        public inneri zooi;
        public inneri yooi;
        public inner(int ia, String ib) {
            this.ia = ia;
            this.ib = ib;
            this.ii = new inner2(ia+1, ib);
            class woopinneri extends inneri {
                int x = 5;
                void duh() {
                    super.iii = 3;
                }
            }
            this.zooi = new woopinneri();
            this.yooi = new inneri() { 
                int yx = 6;
                void yduh() {
                    super.iii = 4;
                }
            };
        }
        public String toString() {
            return "[inner ia " + ia + "  ib " + ib + "  ii " + ii.toString() + "]";
        }
        public class inneri implements Serializable {
            private int iii;
        }

        public class inner2 extends inneri implements Serializable {
            private int ia2;
            private String ib2;
            public inner2(int ia2, String ib2) {
                super();
                this.ia2 = ia2;
                this.ib2 = ib2;
            }
            public String toString() {
                return "[inner2 a: " + a + " ia2 " + ia2 + "  ib2 " + ib2 + "]";
            }
        }
    }
    private int a;
    private String b;
    private inner i;
    private int[] ai;
    private istatic ist;

    public String toString() {
        return "[blob4 a " + a + "  b " + b + "  i " + i.toString() + "]";
    }

    public blob4(int a) {
        String b = "zoo";
        this.a = a;
        this.b = b;
        this.ist = new istatic(a, b);
        this.i = new inner(a+1, b);
        this.ai = new int[] { a+1, a+2, a+3, a+4, a+5, };
    }
}
