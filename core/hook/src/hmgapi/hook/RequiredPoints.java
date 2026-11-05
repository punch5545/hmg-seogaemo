package hmgapi.hook;

// HMG-Seogaemo core hook host. AGPL-3.0-or-later; engine API derived from HMG-API (see UPSTREAM.md).
/** The core host declares no per-app points; each module checks the points it uses. */
final class RequiredPoints {
    private RequiredPoints() {}
    static String[] forPart(String app) { return new String[0]; }
}
