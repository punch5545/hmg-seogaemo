package hmgapi.mods.wireless.dipo;

/** Wireless CarPlay call state: when the bridge subscribes, retries, and stale tries. */
public final class CallStateTest {
    static final int ID = CallState.IDENTIFICATION_ACCEPTED, TRANSPORT_ID = 24;

    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }

    static void eq(String want, String got, String message) {
        check(want == null ? got == null : want.equals(got), message + ": wanted [" + want + "], got [" + got + "]");
    }

    /** answers per subscription from a script (true, false, or null to throw) and counts calls */
    static final class Fake implements CallState.Server {
        final Boolean[][] answers;
        final int[] calls = new int[2];

        Fake(Boolean[] state, Boolean[] updates) { answers = new Boolean[][] { state, updates }; }

        @Override
        public boolean start(int i) throws Exception {
            Boolean[] a = answers[i];
            Boolean r = a[Math.min(calls[i], a.length - 1)];
            calls[i]++;
            if (r == null) throw new java.io.IOException("binder died");
            return r;
        }
    }

    static Boolean[] b(Boolean... v) { return v; }

    public static void main(String[] args) {
        CallState c = new CallState();
        check(!c.identified(false, ID), "USB or no channel: no subscribe");
        check(!c.identified(true, TRANSPORT_ID), "other notification: no subscribe");
        check(c.identified(true, ID), "channel identification: subscribe");
        check(!c.identified(true, ID), "same session: once");

        int s = c.session();
        Fake ok = new Fake(b(true), b(true));
        eq("CarPlay call state: call state started, call updates started (try 1)", c.attempt(s, 1, true, ok), "both on the first try");
        check(ok.calls[0] == 1 && ok.calls[1] == 1, "one call each");

        // A refused subscription is retried alone; the started one is not called again.
        c.reset();
        check(c.identified(true, ID), "new session");
        int s2 = c.session();
        eq("", c.attempt(s, 1, true, ok), "try from the old session does nothing");
        check(ok.calls[0] == 1, "no call for the old session");
        Fake late = new Fake(b(true), b(false, true));
        eq(null, c.attempt(s2, 1, true, late), "updates refused: try again");
        eq("CarPlay call state: call updates started (try 2)", c.attempt(s2, 2, true, late), "updates on the second try");
        check(late.calls[0] == 1 && late.calls[1] == 2, "call state not repeated");
        eq("", c.attempt(s2, 1, false, late), "channel gone: nothing");

        // Failing every try: gives up with the reason; the next identification may subscribe again.
        c.reset();
        c.identified(true, ID);
        int s3 = c.session();
        Fake dead = new Fake(b((Boolean) null), b(true));
        eq(null, c.attempt(s3, 1, true, dead), "binder error: try again");
        eq(null, c.attempt(s3, 2, true, dead), "binder error: try again (2)");
        eq("CarPlay call state: call state failed: java.io.IOException: binder died (try 3)", c.attempt(s3, 3, true, dead), "gives up");
        check(dead.calls[1] == 1, "updates subscribed once");
        check(c.identified(true, ID), "after giving up: next identification subscribes");

        // The session changes while a retry is pending.
        c.reset();
        c.identified(true, ID);
        int s4 = c.session();
        Fake never = new Fake(b(false), b(false));
        c.attempt(s4, 1, true, never);
        c.reset();
        eq("", c.attempt(s4, 2, true, never), "stale retry after reset");
        check(c.identified(true, ID), "new session subscribes normally");

        // A server without the method (NoSuchMethodError) is caught like any failure.
        c.reset();
        c.identified(true, ID);
        int s5 = c.session();
        CallState.Server missing = new CallState.Server() {
            @Override public boolean start(int i) { throw new NoSuchMethodError("Iap2SvcTelephonyStartCallState"); }
        };
        c.attempt(s5, 1, true, missing);
        c.attempt(s5, 2, true, missing);
        String r = c.attempt(s5, 3, true, missing);
        check(r != null && r.contains("NoSuchMethodError") && r.endsWith("(try 3)"), "missing method: " + r);

        System.out.println("ok CallState");
    }
}
