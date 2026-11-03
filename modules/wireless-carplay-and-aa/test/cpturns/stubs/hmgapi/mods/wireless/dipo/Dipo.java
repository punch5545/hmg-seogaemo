package hmgapi.mods.wireless.dipo;

/** host stand-in: CpTurns only logs through it and asks it for the settings */
final class Dipo {
    static String laneMode = "off";
    static boolean trace = false;
    static String lastLog = "";
    static final java.util.List<String> logs = new java.util.ArrayList<String>();

    static void log(String m) {
        lastLog = m;
        logs.add(m);
    }

    static String laneMode() {
        return laneMode;
    }

    static boolean trace() {
        return trace;
    }
}
