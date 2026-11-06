package hmgapi.mods.wireless.dipo;

/** Host proof of selector invariants; physical geometry remains an approximation. */
public final class CpJunctionsTest {
    private static int checks;
    private static void check(String label, boolean ok) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }
    private static CpTurns.Maneuver maneuver(int type, Integer angle, int... otherRoads) {
        CpTurns.Maneuver m = new CpTurns.Maneuver(0, 1, null);
        m.type = type; m.hasExit = angle != null; m.exit = angle == null ? 0 : angle;
        m.angles = otherRoads;
        return m;
    }
    private static CpLaneDecoder.Lane lane(int status, int highlight, int... angles) {
        return new CpLaneDecoder.Lane(0, status, angles, highlight);
    }
    private static CpLaneDecoder.Info lanes(CpLaneDecoder.Lane... lanes) {
        return new CpLaneDecoder.Info(1, 0, lanes, 0);
    }
    /** count neutral lanes (no recommendation, no arrows) when no decoded lanes are given */
    private static CpLaneDecoder.Info width(CpLaneDecoder.Info lanes, int count) {
        if (lanes != null || count <= 0) return lanes;
        CpLaneDecoder.Lane[] l = new CpLaneDecoder.Lane[count];
        for (int k = 0; k < count; k++) l[k] = new CpLaneDecoder.Lane(k, 0, new int[0], 1000);
        return lanes(l);
    }
    private static int[] select(CpTurns.Maneuver m, CpLaneDecoder.Info lanes, int count) {
        return CpJunctions.select(m, null, width(lanes, count));
    }
    private static int picture(int[] selected) { return selected == null ? -1 : selected[0]; }
    private static int layout(int[] selected) {
        for (int p = 0; p < CpJunctionTable.PICTURES.length; p += 4)
            if (CpJunctionTable.PICTURES[p] == picture(selected)) return CpJunctionTable.PICTURES[p + 1];
        return -1;
    }
    private static int route(int[] selected) {
        for (int h = 0; h < CpJunctionTable.HIGHLIGHTS.length; h += 5)
            if (selected != null && CpJunctionTable.HIGHLIGHTS[h + 1] == selected[1])
                return CpJunctionTable.HIGHLIGHTS[h + 2];
        return -1;
    }
    private static void pair(String label, int[] selected, int a, int b) {
        check(label, selected != null && selected[0] == a && selected[1] == b && selected[2] == 65535);
    }
    /** n lanes, the recommended one(s) on the given edge (-1 left, 1 right) */
    private static CpLaneDecoder.Info edge(int n, int side, int recommended) {
        CpLaneDecoder.Lane[] l = new CpLaneDecoder.Lane[n];
        for (int k = 0; k < n; k++) {
            boolean rec = side > 0 ? k >= n - recommended : k < recommended;
            l[k] = new CpLaneDecoder.Lane(k, rec ? 2 : 0, new int[] {rec ? 40 * side : 0}, rec ? 40 * side : 1000);
        }
        return lanes(l);
    }
    public static void main(String[] args) {
        check("no current maneuver", select(null, edge(3, 1, 1), 0) == null);
        pair("observed default right exit", select(maneuver(23, null), edge(3, 1, 1), 0), 10193, 10194);
        CpTurns.Maneuver right = maneuver(23, null), next = maneuver(23, null);
        right.afterRoad = next.afterRoad = "경부고속도로";
        pair("next same-road ramp breaks a tie weakly", CpJunctions.select(right, next, edge(3, 1, 1)), 10168, 10170);
        next.afterRoad = "Different highway";
        pair("unrelated next road does not change default", CpJunctions.select(right, next, edge(3, 1, 1)), 10193, 10194);
        check("left exit", layout(select(maneuver(22, null), edge(3, -1, 1), 0)) == CpJunctionTable.LAYOUT_EXIT_LEFT);
        check("generic ramp takes its side from the lanes", layout(select(maneuver(8, null), edge(3, 1, 1), 0)) == CpJunctionTable.LAYOUT_EXIT_RIGHT);
        // Conservative: only exits, only with decoded lanes recommending the exit edge.
        for (int type : new int[] {0, 1, 2, 3, 5, 13, 14, 49, 50, 51, 52, 53})
            for (Integer angle : new Integer[] {null, -35, 0, 35})
                check("no picture for type " + type, select(maneuver(type, angle, -35, 0, 35), edge(3, 1, 1), 0) == null
                        && select(maneuver(type, angle, -35, 0, 35), edge(3, -1, 1), 0) == null);
        check("no decoded lanes, no picture", select(maneuver(23, null), null, 0) == null);
        check("empty decoded lanes, no picture", select(maneuver(23, null), lanes(), 0) == null);
        check("no recommendation, no picture", select(maneuver(23, null), null, 3) == null);
        check("one-lane ramp has no picture", select(maneuver(23, 40), lanes(lane(2, 40, 40)), 0) == null);
        check("lane change still to come", select(maneuver(23, null), edge(3, -1, 1), 0) == null);
        CpLaneDecoder.Info middle = lanes(lane(0, 1000, 0), lane(0, 1000, 0), lane(0, 1000, 0), lane(2, 20, 20), lane(0, 1000, 90));
        check("middle lane recommendation", select(maneuver(23, null), middle, 0) == null);
        CpLaneDecoder.Info all = lanes(lane(2, 0, 0), lane(2, 40, 40));
        check("every lane recommended tells nothing", select(maneuver(23, null), all, 0) == null);
        CpTurns.Maneuver round = maneuver(23, 45); round.junction = 1;
        check("junction roundabout wins over off-ramp type", select(round, edge(3, 1, 1), 0) == null);
        check("loop requires return angle", layout(select(maneuver(23, 140, 0), edge(3, 1, 1), 0)) == CpJunctionTable.LAYOUT_LOOP_RIGHT);
        check("no fabricated mirrored left loop", select(maneuver(22, -140, 0), edge(3, -1, 1), 0) == null);
        check("Y ramp split", route(select(maneuver(23, 35, -35), lanes(lane(0, 1000, -35), lane(2, 35, 35)), 0)) == CpJunctionTable.ROUTE_RIGHT_BRANCH);
        for (int n = 1; n <= 8; n++) for (int type : new int[] {8, 22, 23}) for (int side : new int[] {-1, 1})
            for (int angle : new int[] {-150, -40, 0, 40, 150}) {
                CpTurns.Maneuver m = maneuver(type, angle, -35, 0, 35);
                int[] result = select(m, edge(n, side, 1), 0);
                if (result == null) continue;
                check("selected highlight is an exit route", route(result) != CpJunctionTable.ROUTE_STRAIGHT
                        && route(result) != CpJunctionTable.ROUTE_MAIN_CURVE && route(result) != CpJunctionTable.ROUTE_LANE_VARIANT);
                check("secondary highlight remains none", result[2] == 65535);
                check("deterministic and pure", java.util.Arrays.equals(result, select(m, edge(n, side, 1), 0)) && m.exit == angle);
                boolean belongs = false;
                for (int h = 0; h < CpJunctionTable.HIGHLIGHTS.length; h += 5)
                    if (CpJunctionTable.HIGHLIGHTS[h + 1] == result[1])
                        belongs = CpJunctionTable.PICTURES[CpJunctionTable.HIGHLIGHTS[h] * 4] == result[0];
                check("B belongs to selected A", belongs);
                for (int q = 0; q < CpJunctionTable.PICTURES.length; q += 4)
                    if (CpJunctionTable.PICTURES[q] == result[0]) check("picture width equals lanes", CpJunctionTable.PICTURES[q + 2] == n);
            }
        check("all observed pictures retained", CpJunctionTable.PICTURES.length == 292 * 4);
        check("all observed highlights retained", CpJunctionTable.HIGHLIGHTS.length == 721 * 5);
        System.out.println("CpJunctionsTest: " + checks + " checks passed");
    }
}
