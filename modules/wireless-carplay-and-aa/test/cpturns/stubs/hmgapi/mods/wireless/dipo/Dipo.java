package hmgapi.mods.wireless.dipo;

/** host stand-in: CpTurns only logs through it and asks it for the settings */
final class Dipo {
    static String laneMode = "off";
    static String lastLog = "";
    static final java.util.List<String> logs = new java.util.ArrayList<String>();

    static void log(String m) {
        lastLog = m;
        logs.add(m);
    }

    static String laneMode() {
        return laneMode;
    }

}
