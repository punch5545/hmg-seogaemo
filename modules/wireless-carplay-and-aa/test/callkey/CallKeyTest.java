package hmgapi.mods.wireless.dipo;

/** The CALL key opens CarPlay's Phone app only on a wireless HookSwitch release with no call and no Siri. */
public final class CallKeyTest {
    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }

    public static void main(String[] args) {
        Integer one = 1, zero = 0;
        check(CallKey.opensPhone(true, "HookSwitch", false, one, zero, zero), "idle release opens");
        check(!CallKey.opensPhone(true, "HookSwitch", true, one, zero, zero), "press does not");
        check(!CallKey.opensPhone(false, "HookSwitch", false, one, zero, zero), "wired stays stock");
        check(!CallKey.opensPhone(true, "Drop", false, one, zero, zero), "Drop does not");
        check(!CallKey.opensPhone(true, "HookSwitch", false, one, one, zero), "ringing or in a call: answer/end only");
        check(!CallKey.opensPhone(true, "HookSwitch", false, one, zero, one), "Siri open");
        check(!CallKey.opensPhone(true, "HookSwitch", false, zero, zero, zero), "CarPlay not connected");
        check(!CallKey.opensPhone(true, "HookSwitch", false, null, null, null), "unknown state");
        System.out.println("ok CallKey");
    }
}
