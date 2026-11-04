package hmgapi.hook;
final class Bridge {
    static int writes;
    static int writeResult = -13;
    static void hookMethod(java.lang.reflect.Member m, MethodHook h) {}
    static byte[] peekNative(long a, int n) { return new byte[n]; }
    static int pokeNative(long a, byte[] b) { writes++; return writeResult; }
    static long mapCodeNative(long a, int n) { return -13; }
}
