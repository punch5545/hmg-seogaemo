package hmgapi.hook;
public final class Points {
    public static int dev;
    public static Object call(ClassLoader cl,String id,Object obj,Object... args) { return dev; }
}
