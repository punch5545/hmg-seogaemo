package hmgapi.mods.wireless.dipo;

/** host stand-in: CpTurns only logs through it and asks it for the settings */
final class Dipo {
    static String lastLog = "";
    static final java.util.List<String> logs = new java.util.ArrayList<String>();

    static void log(String m) {
        lastLog = m;
        logs.add(m);
    }

    static final java.util.List<String> observed = new java.util.ArrayList<String>();
    static int flushes;

    static void observe(boolean lane, String key, String detail, String sample) {
        observed.add((lane ? "lane " : "icon ") + key + " => " + detail + (sample == null || sample.length() == 0 ? "" : " text=" + sample));
    }

    static void flushObservations(boolean force) {
        flushes++;
    }

    static boolean dev = true;

    static boolean dev() {
        return dev;
    }

}
