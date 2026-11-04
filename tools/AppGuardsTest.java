import com.seogaemo.hmg.core.update.BoundedProcess;
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
        System.out.println("AppGuardsTest ok");
    }

    private static RootResult result(String state, String restart, int exit, String... infoPairs) {
        Map<String, String> values = new HashMap<String, String>();
        for (int i = 0; i < infoPairs.length; i += 2) values.put("info." + infoPairs[i], infoPairs[i + 1]);
        return new RootResult(state, restart, "", exit, values);
    }

    private static void restoreGate() {
        RootResult noStock = result("off", "none", 0);
        List<RootResult> quiet = Arrays.asList(result("on", "none", 0, "desired", "on"), result("off", "none", 0));
        check(RestoreGate.decide(noStock, quiet) == RestoreGate.Decision.PROCEED, "clean state proceeds");
        check(RestoreGate.decide(result("conflict", "none", 3, "module_wifi_client", "conflict"), quiet)
                == RestoreGate.Decision.PAUSED, "stock conflict pauses");
        check(RestoreGate.decide(result("off", "none", 0, "module_wifi_client", "reverted"), quiet)
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
