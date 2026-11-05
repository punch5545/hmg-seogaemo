package hmgapi.hook;
public final class Observations {
    public static String key, detail;
    public static int notes, flushes;
    public Observations(String name,int max,int shards) {
        if (!name.equals("tbt-lanes") || max!=512 || shards!=4) throw new AssertionError();
    }
    public void note(String k,String d) { key=k;detail=d;notes++; }
    public void flush(boolean force) { flushes++; }
}
