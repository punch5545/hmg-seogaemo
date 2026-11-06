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
        System.out.println("AaTurnsTest ok");
    }
}
