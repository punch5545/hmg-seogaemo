package hmgapi.hook;
final class Hooks {
    static Class<?> findClass(String name, ClassLoader cl) {
        try { return Class.forName(name); } catch (Exception e) { throw new IllegalArgumentException(e); }
    }
}
