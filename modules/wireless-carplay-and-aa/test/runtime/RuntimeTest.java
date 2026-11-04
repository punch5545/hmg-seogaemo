package hmgapi.hook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import org.json.JSONObject;

public final class RuntimeTest {
    static int checks;
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    static void invalid(File f, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(text.getBytes("UTF-8")); out.close();
        try { RuntimeResult.read(f); throw new AssertionError("accepted malformed record"); }
        catch (java.io.IOException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0], "records").getCanonicalFile();
        RuntimeResult.configure(dir, 77, "123");
        RuntimeResult.put("format", "1");
        RuntimeResult.put("boot", "boot-1");
        RuntimeResult.phase("gate", "ready", "installed");
        RuntimeResult.put("message", "hello\nstate=ready\r\u0000");
        File result = new File(dir, "p-77-123.txt");
        Map<String,String> record = RuntimeResult.read(result);
        check("ready".equals(record.get("gate.state")), "phase persisted");
        check(!record.containsKey("state"), "newline injection sanitized");
        check(record.get("message").equals("hello state=ready  "), "control characters removed");
        check(!new File(dir, "p-77-123.txt.tmp").exists(), "atomic rename consumed temporary");
        android.os.Process.pid = 78;
        RuntimeResult.phase("gate", "off", "fork must not update parent");
        check("ready".equals(RuntimeResult.read(result).get("gate.state")), "fork PID blocks inherited writer");
        android.os.Process.pid = 77;
        for (int i=0; i<2000; i++) RuntimeResult.log("I", "ring message " + i, null);
        check(new File(dir, "p-77-123.log.1").isFile(), "ring rotated");
        check(new File(dir, "p-77-123.log").length() <= RuntimeResult.LIMIT, "current log bounded");
        check(new File(dir, "p-77-123.log.1").length() <= RuntimeResult.LIMIT, "prior log bounded");
        File bad = new File(args[0], "bad.txt");
        invalid(bad, "format=1\na=b\na=c\n");
        invalid(bad, "format=1\na=b");
        invalid(bad, "format=1\na=b\rc\n");
        invalid(bad, "format=2\n");
        StringBuilder huge = new StringBuilder("format=1\na=");
        for (int i=0; i<70000; i++) huge.append('x');
        invalid(bad, huge.append('\n').toString());
        try { RuntimeResult.configure(new File(args[0], "absent/child"), 77, "123");
            throw new AssertionError("missing parent accepted"); } catch (java.io.IOException expected) { checks++; }
        // Diagnostic API must swallow denied/unavailable I/O.
        RuntimeResult.put("io.failure", "survives");
        RuntimeResult.configure(dir, 77, "123");
        for (int i = 0; i < 15; i++) {
            FileOutputStream out = new FileOutputStream(new File(dir, "p-99-" + i + ".txt"));
            out.write("format=1\n".getBytes("UTF-8")); out.close();
            new File(dir, "p-99-" + i + ".log").createNewFile();
        }
        RuntimeResult.put("prune", "run");
        int count = 0;
        for (File f : dir.listFiles()) if (f.getName().endsWith(".txt")) count++;
        check(count <= RuntimeResult.KEEP, "process records bounded");
        for (int i = 0; i < 15; i++) new File(dir, "p-88-" + i + ".txt.tmp").createNewFile();
        RuntimeResult.put("prune", "orphans");
        java.util.Set<String> stems = new java.util.HashSet<String>();
        for (File f : dir.listFiles()) stems.add(f.getName().split("[.]")[0]);
        check(stems.size() <= RuntimeResult.KEEP, "orphan process artifacts bounded");
        File alias = new File(args[0], "alias.txt");
        java.nio.file.Files.createSymbolicLink(alias.toPath(), result.toPath());
        try { RuntimeResult.read(alias); throw new AssertionError("symlink accepted"); }
        catch (java.io.IOException expected) { checks++; }
        check(RuntimeResult.startTicks("77 (process ) name) S" + new String(new char[18]).replace("\0", " 0") + " 123 0").equals("123"), "proc comm parentheses parsed");
        Points.points.clear();
        Points.points.put("unrelated.point", new java.util.HashMap<String,Object>());
        check(!Points.complete("dipo"), "nonempty old map cannot enable DiPO");
        check(!Points.need("dipo", "dipo"), "requirements enforced");
        record = RuntimeResult.read(result);
        check(record.get("part.dipo.missing").contains("dipo.carplay.session_started"), "missing IDs persisted");
        for (String part : new String[] {"boot", "dipo", "iap2", "gpm", "gpm_usb", "navigation", "btsettings"}) {
            Points.points.clear();
            String[] ids = RequiredPoints.forPart(part);
            for (String id : ids) Points.points.put(id, new java.util.HashMap<String,Object>());
            check(Points.complete(part), part + " complete");
            for (String id : ids) {
                Map<String,Object> entry = Points.points.remove(id);
                check(!Points.complete(part), part + " rejects absent " + id);
                Points.points.put(id, entry);
            }
        }
        check(!Points.complete("future-part"), "unknown part closed");
        JSONObject all = new JSONObject().put("gpm.new", new JSONObject().put("class", "java.lang.String"));
        Points.use(new JSONObject().put("points", all));
        check(Points.has("gpm.new") && !Points.has("iap2.service"), "map reload clears stale entries");
        check(!Points.needAll("native", "framework.runtime.load", "framework.runtime.load_library"), "all explicit requirements checked");
        check(NativePatch.errno(13).equals("13:EACCES"), "errno name");
        check(!NativePatch.MEMORY.poke(0, new byte[1]) && NativePatch.lastErrno == 13, "negative native errno captured");
        check(NativePatch.MEMORY.mapCode(0, 4096) == 0 && NativePatch.lastErrno == 13, "mmap errno returned atomically with syscall failure");
        NativePatch.wants.clear();
        NativePatch.request(new ModContext(), "libTest.so", new java.util.ArrayList<hmgapi.engine.MemRecipe>());
        check(NativePatch.wants.isEmpty(), "missing native load points reject registration");
        check("failed".equals(RuntimeResult.read(result).get("native.libTest.so.state")), "native registration failure persisted");
        for (String id : new String[] {"framework.runtime", "framework.runtime.load", "framework.runtime.load_library"})
            Points.points.put(id, new java.util.HashMap<String,Object>());
        NativePatch.hooked = true;
        java.util.List<hmgapi.engine.MemRecipe> recipes = new java.util.ArrayList<hmgapi.engine.MemRecipe>();
        recipes.add(hmgapi.engine.MemRecipe.parse("id test\nverify 0 1 93b885adfe0da089cdf634904fd59f71 -\npatch 0 01\n"));
        NativePatch.request(new ModContext(), "libTest.so", recipes);
        check("registered".equals(RuntimeResult.read(result).get("native.libTest.so.state")), "unloaded library waits for application");
        java.lang.reflect.Method apply = NativePatch.class.getDeclaredMethod("apply", NativePatch.Want.class, String.class, long.class, long.class);
        apply.setAccessible(true);
        NativePatch.Want want = NativePatch.wants.get(0);
        int writes = Bridge.writes;
        apply.invoke(null, want, "/system/lib/libTest.so", 0L, 1L);
        record = RuntimeResult.read(result);
        check("failed".equals(record.get("native.libTest.so.state")) && "13:EACCES".equals(record.get("native.libTest.so.errno")), "recipe write failure records errno");
        check(Bridge.writes == writes + 1, "recipe attempted once");
        want.done = true;
        NativePatch.request(new ModContext(), "libTest.so", recipes);
        NativePatch.tryAll();
        check(NativePatch.wants.size() == 1 && Bridge.writes == writes + 1, "failed recipe never retried by request or load callback");
        Bridge.writeResult = 0;
        apply.invoke(null, want, "/system/lib/libTest.so", 0L, 1L);
        record = RuntimeResult.read(result);
        check("applied".equals(record.get("native.libTest.so.state")) && "0".equals(record.get("native.libTest.so.errno")), "successful recipe clears errno and records application");
        // the iap2 lane msgId data recipe against a byte array shaped like the stock table
        final byte[] mem = new byte[0x32000];
        mem[0x31c50] = 0x01; mem[0x31c51] = 0x52;
        mem[0x31c68] = 0x02; mem[0x31c69] = 0x52;
        mem[0x31c80] = 0x02; mem[0x31c81] = 0x52;
        hmgapi.engine.MemRecipe.Memory ram = new hmgapi.engine.MemRecipe.Memory() {
            public byte[] peek(long a, int n) { return java.util.Arrays.copyOfRange(mem, (int) a, (int) a + n); }
            public boolean poke(long a, byte[] d) { return false; }
            public boolean pokeData(long a, byte[] d) { System.arraycopy(d, 0, mem, (int) a, d.length); return true; }
            public long mapCode(long h, int n) { return 0; }
        };
        java.io.File rf = new java.io.File(System.getProperty("recipes", "payload/recipes"), "iap2-lane-msgid.recipe");
        hmgapi.engine.MemRecipe lane = NativePatch.readOne(rf);
        check(lane.data, "lane recipe is a data recipe");
        hmgapi.engine.MemRecipe.Result lr = lane.apply(ram, 0, 0x2e334, null);
        check(lr.status == hmgapi.engine.MemRecipe.Result.APPLIED && mem[0x31c80] == 0x04 && mem[0x31c81] == 0x52 && mem[0x31c68] == 0x02, "lane msgId 0x5202 -> 0x5204 only in row 3");
        mem[0x31c80] = 0x09;
        check(lane.apply(ram, 0, 0x2e334, null).status == hmgapi.engine.MemRecipe.Result.MISMATCH && mem[0x31c80] == 0x09, "other bytes: fail closed, nothing written");
        // bounded, atomic, rate-limited observation records
        File od = new File(new File(args[0]).getCanonicalFile(), "obs");
        od.mkdirs();
        RuntimeResult.configure(od, android.os.Process.myPid(), "9");
        Observations.clock = 1000;
        Observations ob = new Observations("tbt-icons");
        for (int k = 0; k < 300; k++) ob.note("type=" + k, "icon=1 kind=blank");
        check(ob.size() == Observations.MAX_KEYS, "keys capped at " + Observations.MAX_KEYS);
        ob.flush(false);
        File of = new File(od, "tbt-icons.txt");
        check(of.isFile() && !new File(od, "tbt-icons.txt.tmp").exists() && of.length() <= Observations.MAX_BYTES, "written atomically (no .tmp left) within the byte cap");
        Observations.clock = 1000 + 10000;
        ob.note("type=1000", "icon=2 kind=band");
        long len0 = of.length();
        ob.flush(false);
        check(of.length() == len0, "a second write within 30 s is skipped");
        ob.flush(true);
        Observations.clock = 1000 + 40000;
        for (int k = 0; k < 5; k++) ob.note("type=7", "icon=3 kind=exact");
        ob.note("type=7", "icon=3 kind=exact");
        for (int k = 0; k < 300; k++) ob.note("fresh=" + k, "d");
        ob.flush(true);
        String text = RuntimeResult.text(of, Observations.MAX_BYTES);
        check(text.startsWith("format=1\nkind=tbt-icons\n") && text.contains("type=7|icon=3 kind=exact|6|"), "most-used key survives eviction with its count");
        Observations again = new Observations("tbt-icons");
        again.note("type=7", "icon=3 kind=exact");
        again.flush(true);
        check(RuntimeResult.text(of, Observations.MAX_BYTES).contains("type=7|icon=3 kind=exact|7|"), "counts persist across a restart (file read back)");
        StringBuilder longDetail = new StringBuilder();
        for (int k = 0; k < 400; k++) longDetail.append('x');
        Observations big = new Observations("tbt-lanes");
        for (int k = 0; k < 256; k++) big.note(longDetail.substring(0, 150) + k, longDetail.toString());
        big.flush(true);
        check(new File(od, "tbt-lanes.txt").length() <= Observations.MAX_BYTES, "byte cap holds with long fields (evicts)");
        check(Observations.clean("a|b\nc").equals("a_b_c"), "separators and control characters never reach the file");
        Observations.clock = -1;
        System.out.println("ok payload runtime results, log rotation, point enforcement, errno: " + checks + " checks");
    }
}
