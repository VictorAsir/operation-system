package util;

/**
 * Standard output to console.
 *
 * @author jiquanxi
 * @date 2021/12/23
 */
public class StdOut {

    public static void println(String fmt) {
        System.out.println(fmt);
    }

    public static void println() {
        System.out.println();
    }

    public static void print(String fmt) {
        System.out.print(fmt);
    }

    public static void print(Object obj) {
        System.out.print(obj);
    }
}
