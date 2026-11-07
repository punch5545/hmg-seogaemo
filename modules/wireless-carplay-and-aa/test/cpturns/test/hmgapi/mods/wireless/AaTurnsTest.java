package hmgapi.mods.wireless;

import android.os.Handler;
import hmgapi.hook.Car;
import hmgapi.hook.Cluster;

/** Android Auto turns: the stock format inside the approach window (AaGuidance), icons outside. */
public final class AaTurnsTest {
    static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    static void mapping(int ev, int side, int angle, String want) {
        int[] r = AaGuidance.stock(ev, side, angle);
        String got = r == null ? "icon" : r[0] + " " + r[1];
        require(got.equals(want), "mapping ev=" + ev + " side=" + side + " angle=" + angle + ": got " + got + ", wanted " + want);
    }

    static String last(Cluster c, String prefix) {
        for (int i = c.calls.size() - 1; i >= 0; i--) if (c.calls.get(i).startsWith(prefix)) return c.calls.get(i);
        return null;
    }

    public static void main(String[] args) {
        final int L = AaTurns.LEFT, R = AaTurns.RIGHT, U = AaTurns.UNSPECIFIED;
        mapping(AaTurns.TURN, R, -1, "13 90"); mapping(AaTurns.TURN, L, -1, "13 270"); mapping(AaTurns.TURN, U, -1, "icon");
        mapping(AaTurns.SLIGHT_TURN, R, -1, "13 45"); mapping(AaTurns.SLIGHT_TURN, L, -1, "13 315");
        mapping(AaTurns.SHARP_TURN, R, -1, "13 135"); mapping(AaTurns.SHARP_TURN, L, -1, "13 225");
        mapping(AaTurns.STRAIGHT, U, -1, "13 0"); mapping(AaTurns.STRAIGHT, L, -1, "icon"); mapping(AaTurns.NAME_CHANGE, U, -1, "13 0"); mapping(AaTurns.NAME_CHANGE, R, -1, "icon");
        mapping(AaTurns.U_TURN, L, -1, "31 270"); mapping(AaTurns.U_TURN, R, -1, "31 90");
        mapping(AaTurns.OFF_RAMP, R, -1, "17 90"); mapping(AaTurns.OFF_RAMP, L, -1, "18 270"); mapping(AaTurns.OFF_RAMP, U, -1, "icon");
        mapping(AaTurns.ON_RAMP, R, -1, "17 90");
        mapping(AaTurns.FORK, R, -1, "icon"); mapping(AaTurns.MERGE, L, -1, "icon");
        mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, 90, "32 90"); mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, 180, "32 0");
        mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, 270, "32 270"); mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, 140, "32 45");
        mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, -1, "icon");
        mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, 0, "icon"); mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, 360, "32 180");
        mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, U, 90, "icon"); mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, 170, "32 45");
        mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, 200, "32 315"); mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, R, 30, "32 180"); mapping(AaTurns.ROUNDABOUT_ENTER_AND_EXIT, L, 90, "icon");
        mapping(AaTurns.ROUNDABOUT_EXIT, R, 90, "icon");
        mapping(AaTurns.DESTINATION, R, -1, "2 90"); mapping(AaTurns.DESTINATION, L, -1, "2 270"); mapping(AaTurns.DESTINATION, U, -1, "3 0");
        mapping(AaTurns.DEPART, R, -1, "icon"); mapping(AaTurns.FERRY_BOAT, U, -1, "icon");
        require(AaGuidance.window(AaTurns.TURN) == 300 && AaGuidance.window(AaTurns.OFF_RAMP) == 600, "windows");
        require(AaGuidance.approach(AaTurns.TURN, 300) && !AaGuidance.approach(AaTurns.TURN, 301)
                && AaGuidance.approach(AaTurns.OFF_RAMP, 600) && !AaGuidance.approach(AaTurns.TURN, -1), "window edges");
        require(AaGuidance.bar(300, 300) == 100 && AaGuidance.bar(150, 300) == 50 && AaGuidance.bar(0, 300) == 0, "bar");

        Car.unit = Car.DISTANCE_KM;
        Cluster c = new Cluster();
        Handler h = new Handler();
        AaTurns t = new AaTurns(new android.content.Context(), c, h);
        ApproachBar.sent.clear();
        t.status(1);
        t.nextTurn("Road", R, AaTurns.TURN, -1, 0);
        t.nextTurnDistance(800, 60, 800, AaTurns.METERS);
        require("tbt 67 65535 90.0 800.0 0 0 0 32 0".equals(last(c, "tbt ")), "far: icon " + last(c, "tbt "));
        require(ApproachBar.sent.isEmpty(), "far: no bar");
        t.nextTurnDistance(150, 10, 150, AaTurns.METERS);
        require("tbt 13 0 90.0 150.0 0 0 0 0 0".equals(last(c, "tbt ")), "near: stock " + last(c, "tbt "));
        require("true 50".equals(ApproachBar.sent.get(ApproachBar.sent.size() - 1)), "near: bar 50");
        require(h.delayed == t.renew, "near: lease renewal scheduled");
        require(last(c, "hud ").startsWith("hud f20143"), "HUD list keeps the projection icon " + last(c, "hud "));
        // a new maneuver needs a fresh distance: its first push keeps the icon
        t.nextTurn("Next", L, AaTurns.TURN, -1, 0);
        require(last(c, "tbt ").startsWith("tbt 70 65535 "), "new maneuver: icon " + last(c, "tbt "));
        require("false 0".equals(ApproachBar.sent.get(ApproachBar.sent.size() - 1)), "new maneuver: bar back");
        t.nextTurnDistance(100, 10, 100, AaTurns.METERS);
        require("tbt 13 0 270.0 100.0 0 0 0 0 0".equals(last(c, "tbt ")), "new maneuver near " + last(c, "tbt "));
        // the next right turn on another road: also a new maneuver
        t.nextTurn("Other", L, AaTurns.TURN, -1, 0);
        require(last(c, "tbt ").startsWith("tbt 70 65535 "), "same type, new road: icon " + last(c, "tbt "));
        t.nextTurnDistance(100, 10, 100, AaTurns.METERS);
        // rerouting: RECALC_ROUTE 10, turns held until the new route's turn
        t.status(3);
        require("tbt 10 0 0.0 0.0 0 0 0 0 0".equals(last(c, "tbt ")), "rerouting");
        require("false 0".equals(ApproachBar.sent.get(ApproachBar.sent.size() - 1)), "rerouting: bar back");
        t.nextTurnDistance(90, 10, 90, AaTurns.METERS);
        require(last(c, "tbt ").startsWith("tbt 10 "), "rerouting holds the turn");
        for (int k = 1; k < AaTurns.REROUTE_HOLD - 1; k++) t.nextTurnDistance(90, 10, 90, AaTurns.METERS);
        require(last(c, "tbt ").startsWith("tbt 10 "), "rerouting holds a few distances");
        t.nextTurnDistance(85, 10, 85, AaTurns.METERS);
        require("tbt 13 0 270.0 85.0 0 0 0 0 0".equals(last(c, "tbt ")), "rerouting never sticks " + last(c, "tbt "));
        t.status(3);
        t.nextTurn("New", R, AaTurns.OFF_RAMP, -1, 0);
        t.nextTurnDistance(500, 30, 500, AaTurns.METERS);
        require("tbt 17 0 90.0 500.0 0 0 0 0 0".equals(last(c, "tbt ")), "after reroute: exit " + last(c, "tbt "));
        // focus back to the car's navigation, then guidance ends
        t.stop();
        require("false 0".equals(ApproachBar.sent.get(ApproachBar.sent.size() - 1)) && h.delayed != t.renew, "stop: bar back");
        t.nextTurn("Dest", L, AaTurns.DESTINATION, -1, 0);
        t.nextTurnDistance(40, 5, 40, AaTurns.METERS);
        require("tbt 2 0 270.0 40.0 0 0 0 0 0".equals(last(c, "tbt ")), "destination on the left " + last(c, "tbt "));
        t.status(2);
        require("false 0".equals(ApproachBar.sent.get(ApproachBar.sent.size() - 1)), "end: bar back");
        lanes();
        System.out.println("AaTurnsTest ok");
    }

    static String hex(byte[] f) {
        StringBuilder b = new StringBuilder("lanes ");
        int end = f.length;
        while (end > 1 && f[end - 1] == 0) end--;
        for (int i = 0; i < end; i++) b.append(String.format("%02x", f[i] & 0xff));
        return b.toString();
    }

    /** the cluster frame for lanes {status, arrow ids...} */
    static String frame(int[]... lanes) {
        byte[] f = new byte[81];
        f[0] = (byte) 0xf1;
        f[1] = (byte) lanes.length;
        for (int k = 0; k < lanes.length; k++) {
            f[3 + k] = (byte) lanes[k][0];
            for (int j = 1; j < lanes[k].length; j++) f[19 + 3 * k + j - 1] = (byte) lanes[k][j];
        }
        return hex(f);
    }

    /** the looper runs what is pending: true when something was */
    static boolean fire(Handler h) {
        boolean any = false;
        for (Runnable r : new java.util.ArrayList<Runnable>(h.pending)) any |= h.fire(r);
        return any;
    }

    static int count(Cluster c, String call) {
        int n = 0;
        for (String x : c.calls) if (x.equals(call)) n++;
        return n;
    }

    /** a live route with a turn 800 m ahead (outside the approach window, so no bar lease is pending) */
    static AaTurns live(Cluster c, Handler h) {
        AaTurns t = new AaTurns(new android.content.Context(), c, h);
        t.status(1);
        t.nextTurn("Road", AaTurns.RIGHT, AaTurns.TURN, -1, 0);
        t.nextTurnDistance(800, 60, 800, AaTurns.METERS);
        return t;
    }

    /**
     * Lanes (event 34): "nn" lanes, then per lane the mask of recommended arrow slots and three
     * direction classes (1 straight, 2 slight right, 3 right, 4 sharp right, 5 U-turn, 6 sharp left,
     * 7 left, 8 slight left). Status bytes: 2 bits per slot, 1 white, 2 blue, 3 grey.
     */
    static void lanes() {
        Car.unit = Car.DISTANCE_KM;
        final String ST_R = "01010300", R_ONLY = "01030000";
        // straight + right (right on the route), then a right-only lane on the route
        Cluster c = new Cluster();
        Handler h = new Handler();
        AaTurns t = live(c, h);
        t.nextTurn("02" + "02010300" + "01030000", 0, AaTurns.EV_LANES, -1, -1);
        // glyph pair 16, 18 (straight + right): straight grey, right blue; the single right lane blue
        String f1 = frame(new int[] { 0x0b, 16, 18 }, new int[] { 0x02, 3 });
        require(f1.equals(last(c, "lanes ")), "lanes: " + last(c, "lanes ") + " wanted " + f1);
        require("remain 800 0 0 0".equals(last(c, "remain ")), "lane distance box " + last(c, "remain "));
        // the cluster ages a frame sent once out: it comes again while the route is live
        require(!h.pending.isEmpty(), "lane refresh scheduled");
        int sent = count(c, f1);
        require(fire(h), "lane refresh runs");
        require(count(c, f1) == sent + 1 && !h.pending.isEmpty(), "lane frame sent again, refresh rescheduled");
        fire(h);
        require(count(c, f1) == sent + 2, "lane frame keeps coming");
        // the same lanes from the phone again: no second frame beyond the refresh
        t.nextTurn("02" + "02010300" + "01030000", 0, AaTurns.EV_LANES, -1, -1);
        require(count(c, f1) == sent + 2, "same lanes ignored");
        // distance changes reach the box
        t.nextTurnDistance(650, 40, 650, AaTurns.METERS);
        require("remain 650 0 0 0".equals(last(c, "remain ")), "lane distance follows " + last(c, "remain "));
        // rerouting: the old lanes are not renewed
        t.status(3);
        int again = count(c, f1);
        fire(h);
        require(count(c, f1) == again && !h.pending.isEmpty(), "no refresh while rerouting");
        t.status(1);
        require(count(c, f1) == again + 1, "active again: lanes back");
        // no lanes: empty frame, no refresh, box given back
        t.nextTurn("00", 0, AaTurns.EV_LANES, -1, -1);
        require(frame().equals(last(c, "lanes ")) && h.pending.isEmpty(), "empty lanes: " + last(c, "lanes "));
        require("remain 65535 15 15 0".equals(last(c, "remain ")), "empty lanes: box back " + last(c, "remain "));
        // an end of guidance clears lanes and refresh
        t.nextTurn("01" + R_ONLY, 0, AaTurns.EV_LANES, -1, -1);
        require(!h.pending.isEmpty(), "lanes again");
        t.status(2);
        require(frame().equals(last(c, "lanes ")) && h.pending.isEmpty(), "end: lanes cleared " + last(c, "lanes "));
        require("remain 65535 15 15 0".equals(last(c, "remain ")), "end: box back");
        // the same lanes after a new route are not taken for old ones
        t.status(1);
        t.nextTurn("01" + R_ONLY, 0, AaTurns.EV_LANES, -1, -1);
        require(frame(new int[] { 0x02, 3 }).equals(last(c, "lanes ")), "after a new route: shown again " + last(c, "lanes "));

        // lanes before the active status wait for it
        c = new Cluster();
        h = new Handler();
        t = new AaTurns(new android.content.Context(), c, h);
        t.nextTurn("01" + "01010300", 0, AaTurns.EV_LANES, -1, -1);
        require(last(c, "lanes ") == null && h.pending.isEmpty(), "lanes wait for status");
        t.status(1);
        require(frame(new int[] { 0x0e, 16, 18 }).equals(last(c, "lanes ")), "lanes shown at status " + last(c, "lanes "));

        // no recommendation anywhere: every arrow white; with one lane recommended the others grey
        c = new Cluster();
        h = new Handler();
        t = live(c, h);
        t.nextTurn("03" + "00010300" + "00030000" + "00080000", 0, AaTurns.EV_LANES, -1, -1);
        require(frame(new int[] { 0x05, 16, 18 }, new int[] { 0x01, 3 }, new int[] { 0x01, 8 }).equals(last(c, "lanes ")), "no route lane: all white " + last(c, "lanes "));
        t.nextTurn("03" + "00010300" + "01030000" + "00080000", 0, AaTurns.EV_LANES, -1, -1);
        require(frame(new int[] { 0x0f, 16, 18 }, new int[] { 0x02, 3 }, new int[] { 0x03, 8 }).equals(last(c, "lanes ")), "route lane blue, the rest grey " + last(c, "lanes "));
        // a U-turn (both sides draw arrow 5) with a left lane: the U-turn pair, U-turn on the route
        t.nextTurn("01" + "01050700", 0, AaTurns.EV_LANES, -1, -1);
        require(frame(new int[] { 0x0e, 10, 14 }).equals(last(c, "lanes ")), "u-turn + left " + last(c, "lanes "));
        // a sharp left snaps to the left of a verified set (UT, L, ST): the U-turn on the route blue, the others grey
        t.nextTurn("01" + "04010605", 0, AaTurns.EV_LANES, -1, -1);
        require(frame(new int[] { 0x3e, 26, 31, 22 }).equals(last(c, "lanes ")), "snapped set " + last(c, "lanes "));
        // U-turn right (shape 9) used to drop out of the lane: now its arrow is the U-turn like the left one
        t.nextTurn("01" + "01050000", 0, AaTurns.EV_LANES, -1, -1);
        require(frame(new int[] { 0x02, 5 }).equals(last(c, "lanes ")), "u-turn alone " + last(c, "lanes "));
        // malformed strings change nothing
        int n = c.calls.size();
        t.nextTurn("09", 0, AaTurns.EV_LANES, -1, -1);
        t.nextTurn("01zz", 0, AaTurns.EV_LANES, -1, -1);
        t.nextTurn("0201030000", 0, AaTurns.EV_LANES, -1, -1);
        require(c.calls.size() == n, "malformed lane strings ignored");

        // the focus goes back to the car's navigation: lanes cleared
        c = new Cluster();
        h = new Handler();
        t = live(c, h);
        t.nextTurn("01" + R_ONLY, 0, AaTurns.EV_LANES, -1, -1);
        t.stop();
        require(frame().equals(last(c, "lanes ")) && h.pending.isEmpty(), "focus native clears lanes");
        // unit conversions in the box
        c = new Cluster();
        h = new Handler();
        t = new AaTurns(new android.content.Context(), c, h);
        t.status(1);
        t.nextTurn("Road", AaTurns.RIGHT, AaTurns.TURN, -1, 0);
        t.nextTurnDistance(1250, 80, 1250, AaTurns.METERS);
        t.nextTurn("01" + R_ONLY, 0, AaTurns.EV_LANES, -1, -1);
        require("remain 1 1 3 0".equals(last(c, "remain ")), "km in the box " + last(c, "remain "));
        System.out.println("AaTurnsTest lanes ok");
    }
}
