package hmgapi.hook;

// HMG-Seogaemo core hook host. AGPL-3.0-or-later; engine API derived from HMG-API (see UPSTREAM.md).
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.TreeMap;

/** Bounded key=value result of the zygote pass, written once to core/hook/status. */
final class HostStatus {
    private static final Map<String, String> values = new TreeMap<String, String>();
    private HostStatus() {}

    static synchronized void put(String key, String value) {
        if (values.size() < 64 || values.containsKey(key)) values.put(key, clean(value));
    }
    static void phase(String name, String state, String detail) { put(name, state + ": " + detail); }

    static synchronized void write() {
        File target = new File(HmgApi.ROOT + "/core/hook/status");
        File tmp = new File(target.getPath() + ".tmp");
        try {
            StringBuilder out = new StringBuilder("pid=" + android.os.Process.myPid() + "\n");
            for (Map.Entry<String, String> entry : values.entrySet()) out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            FileOutputStream stream = new FileOutputStream(tmp);
            try { stream.write(out.toString().getBytes("UTF-8")); } finally { stream.close(); }
            if (!tmp.renameTo(target)) tmp.delete();
        } catch (Throwable ignored) { tmp.delete(); }
    }

    private static String clean(String value) {
        String text = value == null ? "" : value.replaceAll("[^\\x20-\\x7e]", "?");
        return text.length() > 180 ? text.substring(0, 180) : text;
    }
}
