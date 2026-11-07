package android.os;

import java.util.ArrayList;
import java.util.List;

/** host stand-in: records the delayed runnables instead of running them (delayed: the last one posted) */
public class Handler {
    public Runnable delayed;
    public final List<Runnable> pending = new ArrayList<Runnable>();
    public boolean post(Runnable r) { r.run(); return true; }
    public boolean postDelayed(Runnable r, long ms) { pending.remove(r); pending.add(r); delayed = r; return true; }
    public void removeCallbacks(Runnable r) {
        pending.remove(r);
        if (delayed == r) delayed = pending.isEmpty() ? null : pending.get(pending.size() - 1);
    }
    /** runs one pending delayed runnable now, as the looper would after its delay */
    public boolean fire(Runnable r) {
        if (!pending.remove(r)) return false;
        if (delayed == r) delayed = pending.isEmpty() ? null : pending.get(pending.size() - 1);
        r.run();
        return true;
    }
}
