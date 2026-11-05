package hmgapi.mods.wireless.dipo;

import com.tcc.iap2.Iap2NotiMsg;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

/**
 * Host test of CpTurns: a drive through iAP2 route guidance messages, checking every
 * cluster call against what the turn-by-turn build's DiPO manager (e/u, e/c, e/v)
 * sends for the same input (worked out from its smali).
 */
public final class CpTurnsTest {
    static int fail;
    static Cluster c;

    static void expect(String what, String... want) {
        List<String> got = c.calls;
        String w = String.join("\n", want), g = String.join("\n", got);
        if (w.equals(g)) {
            System.out.println("ok    " + what);
        } else {
            System.out.println("FAIL  " + what + "\n  wanted:\n    " + w.replace("\n", "\n    ") + "\n  got:\n    " + g.replace("\n", "\n    "));
            fail = 1;
        }
        got.clear();
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) fail = 1;
    }

    static String utf16(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(String.format("%02x%02x", ch & 0xff, ch >> 8));
        String r = b.toString();
        while (r.endsWith("00")) r = r.substring(0, r.length() - 2);
        return r;
    }

    /** iAP2 types: 1/5 small ints, 6 uint32, 7 uint64, 8 enum, 10 string, 13 groups */
    static final int U8 = 1, U16 = 5, U32 = 6, U64 = 7, ENUM = 8, STR = 10, GROUPS = 13;

    static Iap2NotiMsg msg(Object... items) {
        Iap2NotiMsg m = new Iap2NotiMsg();
        for (int i = 0; i < items.length; i += 3) m.getItemGroup().add((Integer) items[i], (Integer) items[i + 1], items[i + 2]);
        return m;
    }

    static int[] i(int... v) {
        return v;
    }

    static long[] l(long... v) {
        return v;
    }

    static String[] s(String v) {
        return new String[] { v };
    }

    /** a RouteGuidanceManeuverUpdate for component 1 */
    static Iap2NotiMsg maneuver(int index, int type, int side, int junction, int exit, String after, int distance) {
        return msg(0, U16, i(1), 1, U16, i(index), 3, ENUM, i(type), 4, STR, s(after), 5, U32, i(distance),
                8, ENUM, i(side), 9, ENUM, i(junction), 11, U8, i(exit));
    }

    public static void main(String[] a) throws Exception {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        Car.unit = Car.DISTANCE_MILES;
        c = new Cluster();
        final CountDownLatch restarted = new CountDownLatch(1);
        CpTurns t = new CpTurns(new android.content.Context(), c, new Runnable() {
            @Override
            public void run() {
                restarted.countDown();
            }
        });
        final int RIGHT = 2, LEFT = 1, STRAIGHT = 3, UTURN = 4, ARRIVE = 12, MILES = 1, KM = 0, FEET = 4;

        t.maneuver(maneuver(0, RIGHT, 0, 0, 90, "Main St", 300));
        t.maneuver(maneuver(1, LEFT, 0, 0, -90, "Oak Ave", 800));
        t.maneuver(maneuver(2, STRAIGHT, 0, 0, 0, "Elm St", 0));
        t.maneuver(maneuver(3, UTURN, 0, 0, 0, "Pine St", 2000));
        expect("maneuvers alone: nothing sent");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 7, U32, i(5000), 9, ENUM, i(MILES), 11, STR, s("0.2"),
                12, ENUM, i(MILES), 13, U16, i(0, 1, 2, 3), 5, U64, l(1700000000L), 19, STR, s("Maps")));
        expect("right turn in 0.2 mi: crossroad, turn, HUD list with the next turn, HUD distance, trip, turns ahead",
                "road [Main St]", "tbt 67 0 0.0 0.2 0 0 2 0 0", "hud f202434600" + utf16("Main St"), "2c 2 2 0 0 0 0 0 0 0",
                "sub 3.1 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 11, STR, s("0.1")));
        expect("partial update (distance only): same HUD list not resent",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 0 0 0 0", "sub 3.1 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        Iap2NotiMsg lanes = new Iap2NotiMsg();
        Iap2NotiMsg.Iap2ObjGroup l1 = lanes.new Iap2ObjGroup(), l2 = lanes.new Iap2ObjGroup();
        l1.add(1, U16, i(1)).add(2, U16, i(0));
        l2.add(2, U16, i(90, 45)).add(3, U16, i(90));
        lanes.getItemGroup().add(0, U16, i(1)).add(2, GROUPS, new Iap2NotiMsg.Iap2ObjGroup[] { l1, l2 });
        t.lanes(lanes);
        StringBuilder z = new StringBuilder();
        for (int k = 0; k < 14; k++) z.append("00");
        expect("lanes: straight; right + slight right highlighted", "lanes f102000102" + z + "0100000302");

        Car.unit = Car.DISTANCE_KM;
        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(800), 11, STR, s("1.5"), 12, ENUM, i(KM)));
        expect("km: turn in the phone's km, trip in m (owner's km, under 1 km), turns ahead in m/km",
                "road [Main St]", "tbt 67 0 0.0 1.5 0 0 1 0 0", "2c 1 5 1 0 0 0 0 0 0", "sub 800.0 0 0 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] 800.0 0} {2 72 0 [Pine St] [] 2.0 1}");
        Car.unit = Car.DISTANCE_MILES;

        t.maneuver(msg(0, U16, i(1), 1, U16, i(0), 5, U32, i(250)));
        t.routeGuidance(msg(0, U16, i(1), 11, STR, s("0.1"), 12, ENUM, i(MILES)));
        expect("maneuver update without its exit angle (Apple Maps in Korea sends none): the right-turn type alone gives the right arrow, HUD list not resent",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 0 0 0 0", "sub 0.4 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] 0.49720323 2} {2 72 0 [Pine St] [] 1.2430081 2}");
        t.routeGuidance(msg(0, U16, i(1), 11, STR, s("0.1"), 12, ENUM, i(MILES)));
        expect("(the update above, still without its exit angle: same arrow, HUD list not resent)",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 0 0 0 0", "sub 0.4 0 2 22 13 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] 0.49720323 2} {2 72 0 [Pine St] [] 1.2430081 2}");
        t.maneuver(maneuver(0, RIGHT, 0, 0, 90, "Main St", 250));

        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(100), 6, U64, l(0), 5, U64, l(0)));
        expect("trip under 161 m with miles: feet; no arrival time (same right arrow, HUD list not resent)",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 0 0 0 0", "sub 328.0 0 3 -1 -1 -1 -1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 7, U32, i(0), 6, U64, l(600)));
        Calendar in10 = Calendar.getInstance();
        in10.add(Calendar.SECOND, 600);
        expect("arrival from the time remaining (no distance)",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 0 0 0 0",
                "sub 0.0 -1 0 " + in10.get(Calendar.HOUR_OF_DAY) + " " + in10.get(Calendar.MINUTE) + " 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 6, U64, l(0), 5, U64, l(600000000L)));
        expect("arrival as seconds since 2001 (Apple's epoch): 2020-01-06 10:40",
                "road [Main St]", "tbt 67 0 0.0 0.1 0 0 2 0 0", "2c 2 1 0 0 0 0 0 0 0", "sub 0.0 -1 0 10 40 1 1 0.0 -1 0",
                "list {1 70 0 [Oak Ave] [] " + (800 / 1609f) + " 2} {2 72 0 [Pine St] [] " + (2000 / 1609f) + " 2}");

        t.routeGuidance(msg(0, U16, i(1), 19, STR, s("Waze"), 5, U64, l(0)));
        check("source change: route guidance restarted", restarted.await(2, TimeUnit.SECONDS));
        expect("source change (Maps to Waze): HUD cleared; the reset state is 0 until the phone resends it, so cleared again",
                "2c 0 0 0 0 0 0 0 0 0", "lanes f1", "hud f2", "hud f2", "2c 0 0 0 0 0 0 0 0 0", "lanes f1", "hud f2", "hud f2");

        t.maneuver(maneuver(5, ARRIVE, 0, 0, 0, "Home Rd", 100));
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(6), 4, STR, s("Home"), 11, STR, s("300"), 12, ENUM, i(FEET), 13, U16, i(5)));
        expect("state 6 with destination, arrival maneuver in 300 ft",
                "dest 0 [Home]", "road [Home Rd]", "tbt 112 0 0.0 300.0 0 0 3 0 0", "hud f201700000" + utf16("Home Rd"),
                "2c 3 0 300 0 0 0 0 0 0");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(2)));
        expect("arrived (state 2): icon 3, no HUD list for it", "road [Home Rd]", "tbt 3 0 0.0 300.0 0 0 3 0 0", "2c 3 0 300 0 0 0 0 0 0");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
        expect("state 0: HUD cleared", "2c 0 0 0 0 0 0 0 0 0", "lanes f1", "hud f2", "hud f2");

        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1)));
        t.stop();
        c.calls.clear();
        t.stop();
        expect("route guidance stopped (car took turn-by-turn back): HUD cleared", "2c 0 0 0 0 0 0 0 0 0", "lanes f1", "hud f2", "hud f2");

        t.disconnected();
        expect("iAP2 session ended: HUD cleared", "2c 0 0 0 0 0 0 0 0 0", "lanes f1", "hud f2", "hud f2");
        t.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 11, STR, s("0.5"), 12, ENUM, i(MILES), 13, U16, i(5), 19, STR, s("Maps")));
        expect("after the session ended: maneuvers gone (HUD distance only), a new source isn't a change", "2c 2 5 0 0 0 0 0 0 0");
        c.calls.clear();

        t.maneuver(msg(0, U16, i(7), 1, U16, i(0), 3, ENUM, i(LEFT), 4, STR, s("Comp 7 Rd"), 8, ENUM, i(0), 9, ENUM, i(0), 11, U8, i(-60)));
        t.routeGuidance(msg(0, U16, i(7), 1, ENUM, i(1), 11, STR, s("0.3"), 12, ENUM, i(MILES), 13, U16, i(0)));
        expect("a second component keeps its own state", "road [Comp 7 Rd]", "tbt 71 0 0.0 0.3 0 0 2 0 0",
                "hud f201470000" + utf16("Comp 7 Rd"), "2c 2 3 0 0 0 0 0 0 0");

        t.maneuver(msg(1, U16, i(0), 3, ENUM, i(STRAIGHT), 4, STR, s("Any Rd"), 8, ENUM, i(0), 9, ENUM, i(0)));
        t.routeGuidance(msg(1, ENUM, i(1), 11, STR, s("0.4"), 13, U16, i(0)));
        expect("no component ids: every known component gets it",
                "road [Any Rd]", "tbt 65 0 0.0 0.4 0 0 2 0 0", "hud f201410000" + utf16("Any Rd"), "2c 2 4 0 0 0 0 0 0 0",
                "road [Any Rd]", "tbt 65 0 0.0 0.4 0 0 2 0 0", "2c 2 4 0 0 0 0 0 0 0");

        t.routeGuidance(msg(0, U16, i(0), 1, ENUM, i(1), 11, STR, s("0.4"), 13, U16, i(0)));
        expect("component 0's first update: DiPO starts it empty (state 0), so the HUD is cleared",
                "2c 0 0 0 0 0 0 0 0 0", "lanes f1", "hud f2", "hud f2");

        check("icons: sharp left 69, slight right 66, roundabout exit 96/97, U-turn left-hand traffic 73",
                CpTurns.icon(1, 0, 0, -120, 1) == 69 && CpTurns.icon(2, 0, 0, 60, 1) == 66 && CpTurns.icon(2, 0, 1, 0, 1) == 96
                        && CpTurns.icon(31, 1, 1, 0, 1) == 96 && CpTurns.icon(4, 1, 0, 0, 1) == 73 && CpTurns.icon(47, 0, 0, -140, 1) == 69
                        && CpTurns.icon(99, 0, 0, 0, 1) == 0);
        Iap2NotiMsg lm = new Iap2NotiMsg();
        Iap2NotiMsg.Iap2ObjGroup pref = lm.new Iap2ObjGroup(), along = lm.new Iap2ObjGroup(), off = lm.new Iap2ObjGroup();
        pref.add(1, U16, i(2)).add(2, U16, i(0)).add(3, U16, i(0));
        along.add(1, U16, i(1)).add(2, U16, i(0, 90)).add(3, U16, i(0));
        off.add(1, U16, i(0)).add(2, U16, i(90));
        check("lane marks, off: preferred 2, route 2, other 1 (as before)",
                CpTurns.laneOne(pref, "off")[0] == 2 && CpTurns.laneOne(along, "off")[0] == 2 && CpTurns.laneOne(off, "off")[0] == 1
                        && CpTurns.laneOne(pref)[0] == 2);
        check("lane marks, rec: only the preferred lane is 2",
                CpTurns.laneOne(pref, "rec")[0] == 2 && CpTurns.laneOne(along, "rec")[0] == 1 && CpTurns.laneOne(off, "rec")[0] == 1);
        check("lane marks, dim0 / dim3: preferred 2, route lane 1, the rest 0 / 3",
                CpTurns.laneOne(pref, "dim0")[0] == 2 && CpTurns.laneOne(along, "dim0")[0] == 1 && CpTurns.laneOne(off, "dim0")[0] == 0
                        && CpTurns.laneOne(off, "dim3")[0] == 3 && CpTurns.laneOne(along, "dim3")[0] == 1);
        Dipo.laneMode = "auto";
        Iap2NotiMsg.Iap2ObjGroup[] withPref = { along, pref, off }, noPref = { along, off };
        check("auto: a preferred lane is named", CpTurns.anyPreferred(withPref) && !CpTurns.anyPreferred(noPref) && !CpTurns.anyPreferred(null));
        Iap2NotiMsg am = new Iap2NotiMsg();
        am.getItemGroup().add(0, U16, i(1)).add(2, GROUPS, withPref);
        t.lanes(am);
        Iap2NotiMsg am2 = new Iap2NotiMsg();
        am2.getItemGroup().add(0, U16, i(1)).add(2, GROUPS, noPref);
        t.lanes(am2);
        Dipo.laneMode = "off";
        expect("auto: with a preferred lane the frame marks 1 2 1; without one the route lane is 2 (marks 2 1)",
                "lanes f103000102010000000000000000000000000001030001000003", "lanes f102000201000000000000000000000000000001030003");
        t.sendHudDist(0.96f, Cluster.UNIT_MI);
        t.sendHudDist(1.97f, Cluster.UNIT_KM);
        t.sendHudDist(0.1f, Cluster.UNIT_MI);
        t.sendHudDist(250f, Cluster.UNIT_M);
        t.sendHudDist(5000f, Cluster.UNIT_M);
        expect("HUD distance to the nearest tenth, carried: 0.96 mi is 1.0, 1.97 km is 2.0, 0.1 mi stays, metres whole, capped at 4095",
                "2c 2 0 1 0 0 0 0 0 0", "2c 1 0 2 0 0 0 0 0 0", "2c 2 1 0 0 0 0 0 0 0", "2c 0 0 250 0 0 0 0 0 0", "2c 0 0 4095 0 0 0 0 0 0");
        check("angles the exact table skips get the nearest arrow: 75 right 67, -75 left 70, 30 right 66, -30 left 71, 150 right 68, -150 left 69",
                CpTurns.icon(2, 0, 0, 75, 1) == 67 && CpTurns.icon(1, 0, 0, -75, 1) == 70 && CpTurns.icon(2, 0, 0, 30, 1) == 66
                        && CpTurns.icon(1, 0, 0, -30, 1) == 71 && CpTurns.icon(2, 0, 0, 150, 1) == 68 && CpTurns.icon(1, 0, 0, -150, 1) == 69);
        check("left-hand traffic: -90 left 70, 90 right 67 (the exact table needed right-hand traffic)",
                CpTurns.icon(1, 1, 0, -90, 1) == 70 && CpTurns.icon(2, 1, 0, 90, 1) == 67);
        check("a type that says sharp or slight keeps it at any angle: 48 sharp right 68, 47 sharp left 69, 49 slight left 71, 50 slight right 66",
                CpTurns.icon(48, 0, 0, 100, 1) == 68 && CpTurns.icon(47, 0, 0, -100, 1) == 69 && CpTurns.icon(49, 0, 0, -10, 1) == 71
                        && CpTurns.icon(50, 0, 0, 100, 1) == 66);
        check("still nothing: no angle (0), a roundabout at an unlisted angle, an unknown type",
                CpTurns.icon(1, 0, 0, 0, 1) == 0 && CpTurns.icon(2, 0, 0, 0, 1) == 0 && CpTurns.icon(1, 0, 1, -75, 1) == 0
                        && CpTurns.icon(99, 0, 0, 75, 1) == 0);
        {
            CpTurns r = new CpTurns(new android.content.Context(), c, null);
            c.calls.clear();
            r.maneuver(maneuver(0, RIGHT, 0, 0, 90, "Main St", 300));
            r.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 7, U32, i(5000), 9, ENUM, i(MILES), 11, STR, s("0.2"), 12, ENUM, i(MILES), 13, U16, i(0)));
            c.calls.clear();
            r.refresh();
            check("refresh right after a phone update sends nothing", c.calls.isEmpty());
            r.hudAt -= CpTurns.HUD_RESEND_MS + 1000L;
            r.refresh();
            check("HUD list resent unchanged after 2 s even while phone updates keep coming (" + c.calls + ")",
                    c.calls.size() == 1 && c.calls.get(0).startsWith("hud f2"));
            c.calls.clear();
            r.refresh();
            check("not again right away", c.calls.isEmpty());
            r.lastSendAt -= 5000L;
            r.refresh();
            boolean again = false;
            for (String k : c.calls) if (k.startsWith("tbt 67 0 0.0 0.2")) again = true;
            check("refresh without fresh phone data resends the turn (" + c.calls + ")", again);
            c.calls.clear();
            r.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(0)));
            c.calls.clear();
            r.lastSendAt -= 5000L;
            r.hudAt -= 5000L;
            r.refresh();
            check("no refresh (and no HUD resend) once guidance state is 0", c.calls.isEmpty() && r.lastHud == null);
        }
        check("match kinds: exact, band, angle, semantic, blank",
                "exact".equals(CpTurns.matchKind(2, 0, 0, 90, 1)) && "semantic".equals(CpTurns.matchKind(2, 0, 0, 0, 1)) && "band".equals(CpTurns.matchKind(2, 0, 0, 75, 1))
                        && "angle".equals(CpTurns.matchKind(8, 0, 0, -75, 1)) && "semantic".equals(CpTurns.matchKind(13, 0, 0, 0, 1))
                        && "blank".equals(CpTurns.matchKind(8, 0, 0, 0, 1)));
        boolean sawBlank = false, sawLane = false;
        for (String o : Dipo.observed) {
            if (o.startsWith("icon type=2 junction=0 side=0 exit~0 => icon=67 kind=semantic exit=0")) sawBlank = true;
            if (o.startsWith("lane n=2")) sawLane = true;
        }
        check("observations: the blank mapping and the lane sighting were recorded (" + Dipo.observed.size() + ")", sawBlank && sawLane && Dipo.flushes > 0);
        check("observations: exit angles keyed by 15-degree buckets", CpTurns.exitBucket(37) == 30 && CpTurns.exitBucket(38) == 45 && CpTurns.exitBucket(-97) == -90 && CpTurns.exitBucket(0) == 0);
        Dipo.dev = false;
        Dipo.logs.clear();
        CpTurns quiet = new CpTurns(new android.content.Context(), new Cluster(), null);
        quiet.routeGuidance(msg(0, U16, i(1), 1, ENUM, i(1), 13, U16, i(0)));
        boolean verbose = false;
        for (String l : Dipo.logs) if (l.startsWith("TBT rg update") || l.startsWith("TBT turn")) verbose = true;
        check("developer toggle off: only the essential first-update line is logged (" + Dipo.logs + ")", !verbose && Dipo.logs.size() == 1);
        Dipo.dev = true;
        check("fallback with an angle, any type: off-ramp (8) -75 slight/normal left 70, 8 at +130 sharp right 68, 8 at +10 straight 65, U-turn by angle 72/73",
                CpTurns.fallbackIcon(8, 0, 0, -75) == 70 && CpTurns.fallbackIcon(8, 0, 0, 130) == 68 && CpTurns.fallbackIcon(8, 0, 0, 10) == 65
                        && CpTurns.fallbackIcon(8, 0, 0, 170) == 72 && CpTurns.fallbackIcon(8, 1, 0, -170) == 73 && CpTurns.fallbackIcon(8, 0, 0, -40) == 71);
        check("fallback roundabout exits by angle: -75 -> 105, 100 -> 99, 130 -> 100, 175 -> 102, -30 -> 107",
                CpTurns.fallbackIcon(7, 0, 1, -75) == 105 && CpTurns.fallbackIcon(7, 0, 1, 100) == 99 && CpTurns.fallbackIcon(7, 0, 1, 130) == 100
                        && CpTurns.fallbackIcon(7, 0, 1, 175) == 102 && CpTurns.fallbackIcon(1, 0, 1, -30) == 107);
        check("fallback by meaning, no angle: keep left 71, keep right 66, enter roundabout 96/97, arrive 112, ferry 176, U-turn 72/73",
                CpTurns.fallbackIcon(13, 0, 0, 0) == 71 && CpTurns.fallbackIcon(14, 0, 0, 0) == 66 && CpTurns.fallbackIcon(6, 0, 1, 0) == 96
                        && CpTurns.fallbackIcon(6, 1, 1, 0) == 97 && CpTurns.fallbackIcon(12, 1, 0, 0) == 112 && CpTurns.fallbackIcon(15, 0, 1, 0) == 176
                        && CpTurns.fallbackIcon(4, 0, 0, 0) == 72 && CpTurns.fallbackIcon(4, 1, 0, 0) == 73);
        check("left/right turn without an angle: the type's arrow (70/67); off-ramp, unknown type, start route -> 64 (no icon)",
                CpTurns.fallbackIcon(1, 0, 0, 0) == 70 && CpTurns.fallbackIcon(2, 0, 0, 0) == 67 && CpTurns.fallbackIcon(8, 0, 0, 0) == 64
                        && CpTurns.fallbackIcon(99, 0, 0, 0) == 64 && CpTurns.fallbackIcon(11, 0, 0, 0) == 64);
        check("turns ahead for sharp/slight types: 47 -> 69, 48 -> 68, 49 -> 71, 50 -> 66",
                CpTurns.aheadIcon(47, 0, 0, 0) == 69 && CpTurns.aheadIcon(48, 0, 0, 0) == 68 && CpTurns.aheadIcon(49, 0, 0, 0) == 71
                        && CpTurns.aheadIcon(50, 0, 0, 0) == 66 && CpTurns.aheadIcon(99, 0, 0, 0) == 64);
        check("lane angles: 0 straight, 90 right, -45 slight left, 180 back",
                CpTurns.laneIcon(0) == 1 && CpTurns.laneIcon(90) == 3 && CpTurns.laneIcon(-45) == 8 && CpTurns.laneIcon(180) == 5
                        && CpTurns.laneIcon(270) == 7);
        System.exit(fail);
    }
}
