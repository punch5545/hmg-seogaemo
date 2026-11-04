package com.seogaemo.hmg.runtime;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class RuntimeConsumerTest {
    static File root, runtime;
    static final Set<String> wrong = new HashSet<String>();
    static HmgSeogaemoProjectionRuntime reader() {
        return new HmgSeogaemoProjectionRuntime(root, runtime, new HmgSeogaemoProjectionRuntime.Metadata() {
            public int[] stat(File f) throws Exception {
                if (!f.exists()) throw new IOException("missing");
                int uid = f.getPath().contains("/data/data/") ? 1000 : 0;
                if (wrong.contains(f.getPath())) uid = 1234;
                return new int[] {uid, f.isDirectory() ? 0040700 : 0100600};
            }
        });
    }
    static void put(File f, String s) throws Exception { f.getParentFile().mkdirs(); Files.write(f.toPath(), s.getBytes("UTF-8")); }
    static File path(String p) { return new File(root, p); }
    static File dir(int i) { return i < 0 ? runtime : path("data/data/" + HmgSeogaemoProjectionRuntime.PACKAGES[i] + "/app_hmg-seogaemo-wireless-runtime"); }
    static File record(int i, int pid) { return new File(dir(i), "p-" + pid + "-900.txt"); }
    static String contents(int i, int pid) throws Exception {
        String pkg = i < 0 ? "zygote" : HmgSeogaemoProjectionRuntime.PACKAGES[i]; int uid = i < 0 ? 0 : 1000;
        StringBuilder stat = new StringBuilder(pid + " (name with spaces) S");
        for (int n = 1; n <= 18; n++) stat.append(" 0"); stat.append(" 900\n");
        put(path("proc/" + pid + "/stat"), stat.toString()); put(path("proc/" + pid + "/cmdline"), pkg + "\0");
        put(path("proc/" + pid + "/status"), "Uid:\t" + uid + "\t" + uid + "\t" + uid + "\t" + uid + "\n");
        HmgSeogaemoProjectionRuntime r = reader();
        String s = "format=1\nboot=boot-two\npid=" + pid + "\nstart_ticks=900\nuid=" + uid + "\npackage=" + pkg + "\nprocess=" + pkg +
            "\npayload_jar_sha256=" + r.digest(path("system/framework/hmg-seogaemo-wireless-hook.jar")) +
            "\npayload_loader_sha256=" + r.digest(path("system/lib/libhmg_seogaemo_wireless_hook.so")) + "\ngate.state=ready\n";
        if (i >= 0) for (String part : HmgSeogaemoProjectionRuntime.PARTS[i]) s += "part." + part + ".state=loaded\n";
        if (i == 0) for (String lib : new String[] {"libCarPlayCore.so", "libCarPlayAudio.so", "libCarPlayUtils.so"}) s += "native." + lib + ".state=applied\n";
        if (i == 2) s += "native.libautoreceiver_infobank.so.state=applied\n"; return s;
    }
    static void expect(String state, String detail) throws Exception {
        HmgSeogaemoProjectionRuntime.Result r = reader().status();
        if (!r.state.equals(state) || !r.detail.contains(detail)) throw new AssertionError(state + "/" + detail + " got " + r.state + "/" + r.detail);
    }
    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        root = new File(args[0]); runtime = path("state/runtime/wireless-carplay-and-aa"); runtime.mkdirs();
        put(path("proc/sys/kernel/random/boot_id"), "boot-two\n"); put(path("system/framework/hmg-seogaemo-wireless-hook.jar"), "jar-A");
        put(path("system/lib/libhmg_seogaemo_wireless_hook.so"), "loader-A"); expect("unknown", "zygote");
        for (int i = -1; i < 5; i++) put(record(i, 101 + i), contents(i, 101 + i)); expect("on", "ready");
        File dipo = record(0, 101); String ready = contents(0, 101);
        put(dipo, ready.replace("boot-two", "boot-one")); expect("unknown", "dipo");
        put(dipo, ready); put(path("proc/101/stat"), "101 (old) S 1\n"); expect("unknown", "dipo");
        contents(0, 101); put(dipo, ready.replace("uid=1000", "uid=2000")); expect("unknown", "dipo");
        put(dipo, ready.replace("process=com.daudio.app.dipo", "process=com.attacker")); expect("unknown", "dipo");
        put(dipo, ready + "format=1\n"); expect("unknown", "dipo"); put(dipo, ready.substring(0, ready.length()-1)); expect("unknown", "dipo");
        put(dipo, ready + "event.bad=control\r\n"); expect("unknown", "dipo");
        put(dipo, ready); wrong.add(dipo.getPath()); expect("unknown", "dipo"); wrong.clear();
        String failed = ready.replace("native.libCarPlayAudio.so.state=applied", "native.libCarPlayAudio.so.state=failed") +
            "native.libCarPlayAudio.so.reason=carplay-audio-formats open failed\nnative.libCarPlayAudio.so.errno=13:EACCES\n";
        put(dipo, failed); expect("failed", "EACCES"); put(record(0, 201), contents(0, 201)); expect("failed", "EACCES");
        put(dipo, failed.replace("boot-two", "boot-one")); expect("on", "ready"); record(0, 201).delete(); put(dipo, ready);
        put(dipo, ready.replace("native.libCarPlayAudio.so.state=applied", "native.libCarPlayAudio.so.state=registered")); expect("unknown", "Audio");
        put(dipo, ready); put(path("system/framework/hmg-seogaemo-wireless-hook.jar"), "jar-B"); expect("pending-restart", "generation");
        put(path("system/framework/hmg-seogaemo-wireless-hook.jar"), "jar-A");
        File gpm = record(2, 103); String gpmReady = contents(2, 103);
        put(gpm, gpmReady.replace("native.libautoreceiver_infobank.so.state=applied", "native.libautoreceiver_infobank.so.applicability=stock")); expect("on", "ready");
        File log = new File(dir(0), "p-101-900.log"), previous = new File(dir(0), "p-101-900.log.1");
        put(log, "current log\n"); put(previous, "previous log\n"); expect("on", "ready");
        File copies = new File(runtime, "apps/" + HmgSeogaemoProjectionRuntime.PACKAGES[0]);
        check(new File(copies, log.getName()).isFile() && new File(copies, previous.getName()).isFile(), "ring collection");
        Files.write(log.toPath(), new byte[65537]); expect("on", "ready"); check(!new File(copies, log.getName()).exists(), "oversized log copied");
        log.delete(); File outside = path("outside"); put(outside, "secret"); Files.createSymbolicLink(log.toPath(), outside.toPath());
        expect("on", "ready"); check(!new File(copies, log.getName()).exists(), "symlink log copied");
        dipo.delete(); Files.createSymbolicLink(dipo.toPath(), outside.toPath()); expect("unknown", "dipo"); dipo.delete(); put(dipo, ready);
        for (int pid = 300; pid < 320; pid++) { put(record(0, pid), contents(0, pid)); put(new File(dir(0), "p-"+pid+"-900.log"), "log"); put(new File(dir(0), "p-"+pid+"-900.log.1"), "old"); }
        HmgSeogaemoProjectionRuntime r = reader(); r.status(); check(r.copied == 32, "global copy budget " + r.copied);
        reader().deleteTree(runtime, 4); check(!runtime.exists() && outside.isFile(), "generated cleanup");
        System.out.println("runtime consumer ready/stale/failure/generation/bounds/aliases/cleanup passed");
    }
}
