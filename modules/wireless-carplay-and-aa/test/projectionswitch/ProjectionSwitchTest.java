package hmgapi.mods.wireless.dipo;

/** Phone Projection's switch: which requests stop the bridged CarPlay, hold the iPhone off and release it. */
public final class ProjectionSwitchTest {
    static final String IPHONE = "80:96:98:05:D7:3D", OTHER = "AA:BB:CC:DD:EE:FF";
    static final int CP = ProjectionSwitch.TYPE_CARPLAY, AA = ProjectionSwitch.TYPE_ANDROID_AUTO;
    static final int WIRELESS = ProjectionSwitch.TRANSFER_WIRELESS, WIRED = 0;

    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }

    public static void main(String[] args) {
        ProjectionSwitch s = new ProjectionSwitch();
        // Nothing bridged, or wired requests: nothing to do, nothing held.
        check(s.request(true, AA, WIRELESS, null, false) == ProjectionSwitch.IGNORE, "no iPhone");
        check(s.request(true, AA, WIRELESS, IPHONE, false) == ProjectionSwitch.IGNORE, "no session");
        check(s.request(true, AA, WIRED, IPHONE, true) == ProjectionSwitch.IGNORE, "wired Android Auto");
        check(s.request(false, CP, WIRED, IPHONE, true) == ProjectionSwitch.IGNORE, "wired CarPlay disconnect");
        check(s.request(false, AA, WIRELESS, IPHONE, true) == ProjectionSwitch.IGNORE, "Android Auto disconnect");
        check(s.request(true, CP, WIRELESS, IPHONE, true) == ProjectionSwitch.IGNORE, "CarPlay connect without a hold");
        check(s.held() == null && !s.stopping(), "nothing held");

        // Android Auto chosen while the bridged CarPlay runs: stop and hold that iPhone.
        check(s.request(true, AA, WIRELESS, IPHONE, true) == ProjectionSwitch.STOP, "Android Auto chosen");
        check(s.isHeld(IPHONE) && s.isHeld(IPHONE.toLowerCase()) && !s.isHeld(OTHER) && !s.isHeld(null), "held iPhone");
        check(s.stopping(), "DiPO must see the session end");
        s.sessionEnded();
        check(!s.stopping() && s.isHeld(IPHONE), "the hold outlives the session");

        // A Bluetooth connection while the iPhone never went away keeps the hold.
        check(!s.connected(IPHONE), "still around");
        s.linkDown(OTHER);
        check(!s.connected(IPHONE), "another phone's link");
        // The next drive: its link went down, then it connects again.
        s.linkDown(IPHONE);
        check(s.connected(IPHONE) && s.held() == null, "released on reconnect");
        check(!s.connected(IPHONE), "released once");

        // CarPlay disconnected in Phone Projection, then chosen again.
        check(s.request(false, CP, WIRELESS, IPHONE, true) == ProjectionSwitch.STOP, "CarPlay disconnected");
        check(s.request(true, CP, WIRELESS, IPHONE, false) == ProjectionSwitch.RESUME && s.held() == null, "CarPlay chosen again");
        check(s.request(true, CP, WIRELESS, IPHONE, false) == ProjectionSwitch.IGNORE, "resumed once");

        // A new hold forgets an old link-down.
        check(s.request(true, AA, WIRELESS, IPHONE, true) == ProjectionSwitch.STOP, "held again");
        s.linkDown(IPHONE);
        check(s.request(true, AA, WIRELESS, IPHONE, true) == ProjectionSwitch.STOP, "chosen again while held");
        check(!s.connected(IPHONE) && s.isHeld(IPHONE), "link-down before the new hold doesn't count");
        System.out.println("ok projection switch: stop, hold and release");
    }
}
