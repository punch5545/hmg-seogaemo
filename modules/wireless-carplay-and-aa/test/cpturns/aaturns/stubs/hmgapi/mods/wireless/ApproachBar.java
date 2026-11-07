package hmgapi.mods.wireless;

/** host stand-in: records the approach-bar broadcasts */
final class ApproachBar {
    static final java.util.List<String> sent = new java.util.ArrayList<String>();
    static void send(android.content.Context c, boolean active, int bar) { sent.add(active + " " + bar); }
}
