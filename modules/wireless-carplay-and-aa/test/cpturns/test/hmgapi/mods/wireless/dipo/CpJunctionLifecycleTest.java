package hmgapi.mods.wireless.dipo;

import hmgapi.hook.Cluster;
import static hmgapi.mods.wireless.dipo.CpTurnsTest.*;
import static hmgapi.mods.wireless.dipo.CpGuidanceTest.require;

public final class CpJunctionLifecycleTest {
    static final String CLEAR = "junction 65535 65535 65535";
    static Cluster cluster;
    static CpTurns turns;
    static void setup(int type, int angle) {
        cluster = new Cluster();
        turns = new CpTurns(new android.content.Context(), cluster, null);
        turns.maneuver(maneuver(0, type, 0, 0, angle, "Expressway exit", 1000));
        turns.maneuver(maneuver(1, type, 0, 0, angle, "Expressway exit", 1000));
    }
    static void distance(int meters) {
        turns.routeGuidance(msg(0,U16,i(1),1,ENUM,i(1),10,U32,i(meters),13,U16,i(0),17,U16,i(3)));
    }
    static int pictures() {
        int count = 0;
        for (String call : cluster.calls) if (call.startsWith("junction ") && !call.equals(CLEAR)) count++;
        return count;
    }
    public static void main(String[] args) {
        setup(23,45); distance(601);
        require(pictures() == 0, "outside 600m window");
        distance(600);
        require(pictures() == 1, "inclusive 600m entry");
        distance(590);
        require(pictures() == 1, "unchanged picture not resent");
        distance(601);
        require(cluster.calls.contains(CLEAR), "leaving window clears persistent view");
        cluster.calls.clear(); distance(500);
        turns.routeGuidance(msg(0,U16,i(1),13,U16,i(1),10,U32,i(450)));
        require(cluster.calls.contains(CLEAR) && pictures() == 2, "replacement clears even identical picture");
        cluster.calls.clear();
        turns.routeGuidance(msg(0,U16,i(1),13,U16,i(0)));
        require(cluster.calls.contains(CLEAR) && pictures() == 0, "new index cannot reuse old distance");

        for (int action = 0; action < 9; action++) {
            setup(23,45); distance(100); cluster.calls.clear();
            switch (action) {
                case 0: turns.stop(); break;
                case 1: turns.disconnected(); break;
                case 2: turns.reset(); break;
                case 3: turns.routeGuidance(msg(0,U16,i(1),1,ENUM,i(0))); break;
                case 4: turns.routeGuidance(msg(0,U16,i(1),1,ENUM,i(2))); break;
                case 5: turns.routeGuidance(msg(0,U16,i(1),1,ENUM,i(3))); break;
                case 6: turns.routeGuidance(msg(0,U16,i(1),1,ENUM,i(5))); break;
                case 7: turns.routeGuidance(msg(0,U16,i(1),13,U16,i())); break;
                default:
                    turns.source = "Maps";
                    turns.routeGuidance(msg(0,U16,i(1),19,STR,s("Waze")));
            }
            require(java.util.Collections.frequency(cluster.calls,CLEAR) == 1, "one clear for path " + action);
            if (action == 0 || action == 1 || action == 3)
                require(cluster.calls.indexOf(CLEAR) < cluster.calls.indexOf("tbt 8 0 0.0 0.0 -1 0 0 0 -1"),
                        "junction cleared before final idle");
            cluster.calls.clear(); turns.stop();
            require(!cluster.calls.contains(CLEAR), "repeated clear is quiet");
        }

        setup(2,90); distance(100);
        require(pictures() == 0, "ordinary urban turn has no junction view");
        // A generic ramp lacks a route side: only its matching recommended lanes supply it.
        setup(8,0);
        turns.routeGuidance(msg(0,U16,i(1),1,ENUM,i(1),10,U32,i(500),13,U16,i(0),16,U16,i(7),18,9,i(1)));
        require(pictures() == 0, "unknown ramp side waits for geometry");
        CpLaneDecoder.Lane[] lanes = {
            new CpLaneDecoder.Lane(0,0,new int[] {0},1000),
            new CpLaneDecoder.Lane(1,2,new int[] {0,45},45)
        };
        turns.rawLanes(new CpLaneDecoder.Info(1,6,lanes,0));
        require(pictures() == 0, "stale lane index cannot select picture");
        turns.rawLanes(new CpLaneDecoder.Info(2,7,lanes,0));
        require(pictures() == 0, "other component cannot select picture");
        turns.rawLanes(new CpLaneDecoder.Info(1,7,lanes,0));
        require(pictures() == 1, "late matching lane update selects independently of stock turn");
        turns.routeGuidance(msg(0,U16,i(1),18,9,i(0)));
        require(cluster.calls.contains(CLEAR), "hiding sole lane evidence clears picture");
        System.out.println("CpJunctionLifecycleTest passed");
    }
}
