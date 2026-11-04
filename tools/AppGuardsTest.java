import com.seogaemo.hmg.core.update.BoundedProcess;

/** Host tests for pure-Java app guards (bounded child process, resume expiry, restore gate). */
public final class AppGuardsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        boundedProcess();
        System.out.println("AppGuardsTest ok");
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
