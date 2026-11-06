import com.seogaemo.hmg.core.update.BoundedProcess;
import com.seogaemo.hmg.module.ModuleResume;
import com.seogaemo.hmg.module.RestoreGate;
import com.seogaemo.hmg.module.RootResult;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Host tests for pure-Java app guards (bounded child process, resume expiry, restore gate). */
public final class AppGuardsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        boundedProcess();
        restoreGate();
        tbtCounters();
        resumeExpiry();
        restoreSteps();
        System.out.println("AppGuardsTest ok");
    }

    private static void resumeExpiry() {
        long hour = 60L * 60L * 1000L;
        long wall = 1800000000000L; // a valid synced clock
        check(!ModuleResume.expired(wall, wall + 23 * hour), "fresh resume kept");
        check(ModuleResume.expired(wall, wall + 25 * hour), "stale resume expires");
        check(!ModuleResume.expired(wall + 25 * hour, wall), "clock moved back is not expiry");
        check(!ModuleResume.expired(0L, wall + 100 * hour), "unset stamp is not expiry");
        check(!ModuleResume.expired(1000L, wall), "stamp from an unsynced clock is not expiry");

        // Same boot: elapsed realtime decides, whatever the wall clock did.
        check(!ModuleResume.expired("b1", 5 * hour, wall, "b1", 5 * hour + 23 * hour, wall + 900 * hour),
                "same boot, wall jump forward is not expiry");
        check(ModuleResume.expired("b1", 5 * hour, wall, "b1", 5 * hour + 25 * hour, wall - 900 * hour),
                "same boot, wall jump back still expires on elapsed");
        // Different boot: wall clock only, and never on a jump back.
        check(!ModuleResume.expired("b1", 5 * hour, wall, "b2", 2 * hour, wall + 23 * hour), "reboot, fresh");
        check(ModuleResume.expired("b1", 5 * hour, wall, "b2", 2 * hour, wall + 25 * hour), "reboot, wall shows 25 h");
        check(!ModuleResume.expired("b1", 5 * hour, wall, "b2", 2 * hour, wall - 25 * hour), "reboot, clock moved back");
        // Unknown boot ids / missing elapsed stamp use the wall fallback.
        check(!ModuleResume.expired("", 0L, wall, "", 30 * hour, wall + 1 * hour), "no boot id, fresh");
        check(ModuleResume.expired(null, 0L, wall, "b2", 1L, wall + 30 * hour), "no saved boot, wall 30 h");
        check(!ModuleResume.expired("b1", 0L, 0L, "b1", 100 * hour, wall), "no stamps at all is not expiry");
    }

    private static void restoreSteps() {
        List<String> ids = Arrays.asList("wifi", "wireless-carplay-and-aa", "wireless-adb");
        List<RootResult> observed = Arrays.asList(
                result("on", "none", 0, "desired", "on"),
                result("off", "none", 0, "desired", "off"),
                result("off", "none", 0));
        List<RestoreGate.Step> steps = RestoreGate.steps("wireless-carplay-and-aa,", ids, observed);
        check(steps.size() == 2, "one off, one on");
        check("wifi".equals(steps.get(0).id) && !steps.get(0).enable, "turn-offs come first");
        check("wireless-carplay-and-aa".equals(steps.get(1).id) && steps.get(1).enable, "saved module turns on");
        check(RestoreGate.touchesBootProcess(steps), "CarPlay change is flagged");
        List<RestoreGate.Step> adb = RestoreGate.steps("wifi,", ids, observed);
        check(adb.isEmpty(), "already as saved needs no step");
        check(!RestoreGate.touchesBootProcess(adb), "no CarPlay flag without a CarPlay step");
        check(RestoreGate.steps("", ids, observed).size() == 1, "empty saved list only turns off");
    }

    private static RootResult result(String state, String restart, int exit, String... infoPairs) {
        Map<String, String> values = new HashMap<String, String>();
        for (int i = 0; i < infoPairs.length; i += 2) values.put("info." + infoPairs[i], infoPairs[i + 1]);
        return new RootResult(state, restart, "", exit, values);
    }

    private static void tbtCounters() {
        RootResult old = result("on", "none", 0, "desired", "on");
        check(old.info.get("tbt_inexact") == null && old.info.get("tbt_blank") == null && old.desiredEnabled(),
                "status output from before the tbt counters parses, the counters are absent");
        RootResult now = result("on", "none", 0, "desired", "on", "tbt_inexact", "3", "tbt_blank", "1");
        check("3".equals(now.info.get("tbt_inexact")) && "1".equals(now.info.get("tbt_blank")), "tbt counters read from status info");
    }

    private static void restoreGate() {
        RootResult noStock = result("off", "none", 0);
        List<RootResult> quiet = Arrays.asList(result("on", "none", 0, "desired", "on"), result("off", "none", 0));
        check(RestoreGate.decide(noStock, quiet) == RestoreGate.Decision.PROCEED, "clean state proceeds");
        check(RestoreGate.decide(result("conflict", "none", 3, "module_wifi", "conflict"), quiet)
                == RestoreGate.Decision.PAUSED, "stock conflict pauses");
        check(RestoreGate.decide(result("off", "none", 0, "module_wifi", "reverted"), quiet)
                == RestoreGate.Decision.PAUSED, "recorded stock restore pauses");
        check(RestoreGate.decide(result("off", "none", 0, "paused", "1"), quiet)
                == RestoreGate.Decision.PAUSED, "explicit paused flag pauses");
        List<RootResult> pending = Arrays.asList(result("on", "none", 0), result("pending-restart", "head_unit", 0));
        check(RestoreGate.decide(noStock, pending) == RestoreGate.Decision.PENDING_RESTART, "pending restart defers");
        check(RestoreGate.decide(result("failed", "none", 1), quiet) == RestoreGate.Decision.UNAVAILABLE,
                "unreadable stock status changes nothing");
    }

    private static void boundedProcess() throws Exception {
        BoundedProcess.Result ok = BoundedProcess.run(
                new ProcessBuilder("sh", "-c", "echo hello; exit 3"), 10000, 4096);
        check(!ok.timedOut && ok.exit == 3 && !ok.truncated, "plain exit");
        check("hello".equals(new String(ok.output, "UTF-8").trim()), "plain output");

        long start = System.currentTimeMillis();
        BoundedProcess.Result slow = BoundedProcess.run(
                new ProcessBuilder("sh", "-c", "sleep 30"), 400, 4096);
        check(slow.timedOut, "timeout flagged");
        check(System.currentTimeMillis() - start < 5000, "timeout returns promptly");

        // Output far larger than the pipe buffer must be drained, not deadlock the child.
        BoundedProcess.Result flood = BoundedProcess.run(
                new ProcessBuilder("sh", "-c", "i=0; while [ $i -lt 4000 ]; do echo 0123456789012345678901234567890123456789; i=$((i+1)); done"),
                20000, 100);
        check(!flood.timedOut && flood.exit == 0, "flood completes");
        check(flood.truncated && flood.output.length == 100, "flood truncated to cap");
    }
}
