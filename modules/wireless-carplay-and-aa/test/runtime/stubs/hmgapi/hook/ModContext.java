package hmgapi.hook;
final class ModContext {
    final String id = "test";
    static String md5(String s) { return ""; }
    void log(String s) {}
    void error(String s, Throwable t) {}
}
