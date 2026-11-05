package hmgapi.hook;
public final class ModContext {
    public int logs;
    public String last;
    public void log(String s) { logs++;last=s; }
}
