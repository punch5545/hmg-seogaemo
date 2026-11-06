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
    private static int[] select(CpTurns.Maneuver m, CpLaneDecoder.Info lanes, int count) {
        return CpJunctions.select(m, null, lanes, count);
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
    public static void main(String[] args) {
        check("no current maneuver", select(null, null, 3) == null);
        pair("observed default right exit", select(maneuver(23, null), null, 3), 10193, 10194);
        CpTurns.Maneuver right = maneuver(23, null), next = maneuver(23, null);
        right.afterRoad = next.afterRoad = "경부고속도로";
        pair("next same-road ramp breaks a tie weakly", CpJunctions.select(right, next, null, 3), 10168, 10170);
        next.afterRoad = "Different highway";
        pair("unrelated next road does not change default", CpJunctions.select(right, next, null, 3), 10193, 10194);
        CpTurns.Maneuver absent = maneuver(8, null);
        check("missing angle and side do not imply right traffic", select(absent, null, 3) == null);
        absent.hasSide = true; absent.side = 1;
        check("known left traffic never chooses branch side", select(absent, null, 3) == null);
        absent.side = 0;
        check("known right traffic never chooses branch side", select(absent, null, 3) == null);
        CpTurns.Maneuver straightUnknown = maneuver(3, 0);
        straightUnknown.description = "Continue on highway past exit";
        straightUnknown.hasSide = true;
        check("straight past unknown exit side cannot use right traffic", select(straightUnknown, null, 3) == null);
        straightUnknown.side = 1;
        check("straight past unknown exit side cannot use left traffic", select(straightUnknown, null, 3) == null);
        absent.side = 1;
        absent.type = 23;
        pair("explicit right maneuver wins over driving side", select(absent, null, 3), 10193, 10194);
        check("ordinary right turn suppressed", select(maneuver(2, 90, 0), null, 3) == null);
        check("straight without split suppressed", select(maneuver(3, 0), null, 3) == null);
        check("roundabout suppressed", select(maneuver(28, 90), null, 3) == null);
        CpTurns.Maneuver round = maneuver(23, 45); round.junction = 1;
        check("junction roundabout wins over off-ramp type", select(round, null, 3) == null);
        CpTurns.Maneuver straight = maneuver(3, 0, 35);
        check("urban straight with a turn lane suppressed", select(straight, null, 3) == null);
        straight.afterRoad = "경부고속도로";
        int[] through = select(straight, null, 3);
        pair("straight past right exit", through, 10193, 10195);
        CpTurns.Maneuver straightLeft = maneuver(3, 0, -35);
        straightLeft.afterRoad = "Expressway";
        check("straight past left exit", layout(select(straightLeft, null, 3)) == CpJunctionTable.LAYOUT_EXIT_LEFT);
        check("keep left fork", layout(select(maneuver(13, -30, 0), null, 3)) == CpJunctionTable.LAYOUT_FORK_LEFT);
        check("keep right fork", layout(select(maneuver(14, 30, 0), null, 3)) == CpJunctionTable.LAYOUT_FORK_RIGHT);
        int[] y = select(maneuver(14, 35, -35), null, 2);
        check("Y route is right branch, not exit", route(y) == CpJunctionTable.ROUTE_RIGHT_BRANCH);
        check("Y left branch", route(select(maneuver(13, -35, 35), null, 2)) == CpJunctionTable.ROUTE_LEFT_BRANCH);
        CpTurns.Maneuver pocket = maneuver(14, 35, -35); pocket.description = "Keep right at zig-zag pocket";
        check("pocket corroborates Y", layout(select(pocket, null, 3)) == CpJunctionTable.LAYOUT_Y_POCKET);
        check("loop requires return angle", layout(select(maneuver(23, 140, 0), null, 3)) == CpJunctionTable.LAYOUT_LOOP_RIGHT);
        check("no fabricated mirrored left loop", select(maneuver(22, -140, 0), null, 3) == null);
        pair("multi/fan right", select(maneuver(23, 30, -40, 0, 70), null, 4), 10619, 10620);
        pair("multi/fan left", select(maneuver(22, -30, 0, 40, 70), null, 4), 10619, 10621);
        CpLaneDecoder.Info all = lanes(lane(0, -35, -35), lane(1, 35, 35), lane(2, 35, 35));
        check("NotGood lane retains opposing Y geometry", route(select(maneuver(8, null), all, 3)) == CpJunctionTable.ROUTE_RIGHT_BRANCH);
        CpLaneDecoder.Info left = lanes(lane(2, -40, -40, 0), lane(1, 40, 40), lane(0, 40, 40));
        check("preferred and good lane votes infer exit side", layout(select(maneuver(8, null), left, 3)) == CpJunctionTable.LAYOUT_EXIT_LEFT);
        CpLaneDecoder.Info omitted = lanes(lane(0, 1000, 0), lane(2, 40, 0), lane(0, 1000, 0));
        pair("highlight valid when omitted from arrows", select(maneuver(8, null), omitted, 3), 10193, 10194);
        CpLaneDecoder.Info ambiguous = lanes(lane(1, 1000, -30, 30), lane(1, 1000, -30, 30));
        check("multiple unhighlighted arrows do not invent side", select(maneuver(8, null), ambiguous, 2) == null);
        CpLaneDecoder.Info two = lanes(lane(0, 1000, 0), lane(1, 40, 40));
        int[] twoResult = select(maneuver(23, 40), two, 5);
        check("decoded lane count overrides fallback", picture(twoResult) == 10046);
        pair("missing decoded lanes uses fallback", select(maneuver(23, null), lanes(), 3), 10193, 10194);
        check("invalid lane angles ignored", select(maneuver(8, null), lanes(lane(1, 1000, 999, -999)), 3) == null);
        CpTurns.Maneuver weak = maneuver(2, 40, 0); weak.afterRoad = "Expressway";
        check("road name corroborates shallow turn with split", select(weak, null, 3) != null);
        weak.exit = 90;
        check("highway name does not authorize ordinary right-angle turn", select(weak, null, 3) == null);
        // All outputs refer to an actual route in the observed table, never a
        // lane-only/blank variant. Exercise the cartesian family/width space.
        for (int type : new int[] {0, 3, 5, 8, 13, 14, 22, 23, 51, 52, 53})
            for (int angle : new int[] {-150, -40, 0, 40, 150})
                for (int width = 0; width <= 8; width++) {
                    CpTurns.Maneuver m = maneuver(type, angle, -35, 0, 35);
                    int[] result = select(m, null, width);
                    if (result == null) continue;
                    check("selected highlight is a supported route", route(result) >= 0 && route(result) != CpJunctionTable.ROUTE_LANE_VARIANT);
                    check("secondary highlight remains none", result[2] == 65535);
                    check("deterministic and pure", java.util.Arrays.equals(result, select(m, null, width)) && m.exit == angle);
                    boolean belongs = false;
                    for (int h = 0; h < CpJunctionTable.HIGHLIGHTS.length; h += 5)
                        if (CpJunctionTable.HIGHLIGHTS[h + 1] == result[1])
                            belongs = CpJunctionTable.PICTURES[CpJunctionTable.HIGHLIGHTS[h] * 4] == result[0];
                    check("B belongs to selected A", belongs);
                }
        check("all observed pictures retained", CpJunctionTable.PICTURES.length == 292 * 4);
        check("all observed highlights retained", CpJunctionTable.HIGHLIGHTS.length == 721 * 5);
        System.out.println("CpJunctionsTest: " + checks + " checks passed");
    }
}
