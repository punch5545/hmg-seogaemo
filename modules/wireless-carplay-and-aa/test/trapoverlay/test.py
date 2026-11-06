#!/usr/bin/env python3
"""Run CpTrapOverlay against a deterministic hook/broadcast/monotonic-clock harness."""
from pathlib import Path
import os
import subprocess
import tempfile

MODULE = Path(__file__).resolve().parents[2]

STUBS = {
    "android/content/Intent.java": r'''
package android.content;
public class Intent {
    private String action, pkg;
    private boolean active;
    private int bar = -1;
    public Intent(String a) { action=a; }
    public String getAction() { return action; }
    public String getPackage() { return pkg; }
    public Intent setPackage(String p) { pkg=p; return this; }
    public Intent putExtra(String k, boolean v) { active=v; return this; }
    public Intent putExtra(String k, int v) { bar=v; return this; }
    public boolean getBooleanExtra(String k, boolean d) { return active; }
    public int getIntExtra(String k, int d) { return bar; }
}
''',
    "android/content/IntentFilter.java": r'''
package android.content;
public class IntentFilter { public IntentFilter(String a) {} }
''',
    "android/content/BroadcastReceiver.java": r'''
package android.content;
public abstract class BroadcastReceiver { public abstract void onReceive(Context c, Intent i); }
''',
    "android/content/Context.java": r'''
package android.content;
public class Context {
    private BroadcastReceiver receiver;
    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f) { receiver=r; return null; }
    public void sendBroadcast(Intent i) { receiver.onReceive(this,i); }
}
''',
    "android/os/Looper.java": r'''
package android.os;
public class Looper { public static Looper getMainLooper() { return new Looper(); } }
''',
    "android/os/SystemClock.java": r'''
package android.os;
public class SystemClock {
    public static long now;
    public static long elapsedRealtime() { return now; }
}
''',
    "android/os/Handler.java": r'''
package android.os;
import java.util.*;
public class Handler {
    private static final Map<Runnable,Long> pending = new HashMap<Runnable,Long>();
    public Handler(Looper l) {}
    public boolean postDelayed(Runnable r,long ms) { pending.put(r,SystemClock.now+ms); return true; }
    public void removeCallbacks(Runnable r) { pending.remove(r); }
    public static void advance(long ms) {
        SystemClock.now += ms;
        for (;;) {
            Runnable due=null;
            for (Map.Entry<Runnable,Long> e : pending.entrySet())
                if (e.getValue()<=SystemClock.now) { due=e.getKey(); break; }
            if (due==null) return;
            pending.remove(due); due.run();
        }
    }
}
''',
    "hmgapi/hook/Points.java": r'''
package hmgapi.hook;
import java.lang.reflect.*;
public final class Points {
    public static Class<?> recordType = CpTrapOverlayTest.Trap.class;
    public static String absent = "";
    public static String missing(String... ids) { return absent; }
    public static Object cluster;
    public static Object newInstance(ClassLoader cl,String id,Object... args) {
        if (!id.equals("framework.navigation_cluster.new") || args.length!=1) throw new AssertionError(id);
        return cluster;
    }
    public static Method method(ClassLoader cl,String id) {
        if (cl!=null) throw new AssertionError("framework loader");
        try {
            if (id.endsWith("_new"))
                return CpTrapOverlayTest.Stock.class.getMethod("modern",recordType);
            Class<?>[] params = new Class<?>[17];
            java.util.Arrays.fill(params,int.class);
            return CpTrapOverlayTest.Stock.class.getMethod("legacy",params);
        } catch (Exception e) { throw new AssertionError(e); }
    }
    public static Class<?> cls(ClassLoader cl,String id) {
        if (!id.equals("framework.tbt_trap_info")) throw new AssertionError(id);
        return recordType;
    }
    public static Constructor<?> ctor(ClassLoader cl,String id) {
        if (!id.equals("framework.tbt_trap_info.new")) throw new AssertionError(id);
        try { return recordType.getConstructor(); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    public static Field field(ClassLoader cl,String id) {
        if (!id.equals("framework.tbt_trap_info.bar_graph_level")) throw new AssertionError(id);
        try { return CpTrapOverlayTest.Trap.class.getField("bar"); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
''',
    "hmgapi/hook/ModContext.java": r'''
package hmgapi.hook;
import java.lang.reflect.*;
import java.util.*;
public final class ModContext {
    private static final Map<String,MethodHook> hooks=new HashMap<String,MethodHook>();
    public int errors;
    public Member hook(ClassLoader cl,String id,MethodHook hook) {
        if (cl!=null) throw new AssertionError("framework hook loader");
        if (hooks.put(id,hook)!=null) throw new AssertionError("duplicate hook");
        return Points.method(null,id);
    }
    public void log(String s) {}
    public void error(String s,Throwable t) { errors++; }
    static boolean enter(String id,Object target,Object[] args) {
        MethodHook h=hooks.get(id);
        if (h==null) return true;
        MethodHook.Param p=new MethodHook.Param(Points.method(null,id),target,args);
        try { h.beforeHookedMethod(p); p.getResultOrThrowable(); }
        catch(Throwable t) { throw new RuntimeException(t); }
        return !p.returnEarly;
    }
}
''',
}

TEST = r'''
package hmgapi.hook;
import android.content.*;
import android.os.*;
import hmgapi.mods.wireless.CpTrapOverlay;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class CpTrapOverlayTest {
    private static final String OLD="framework.navigation_cluster.update_tbt_trap_info";
    private static final String NEW=OLD+"_new";
    private static int assertions;
    public static class BaseTrap { public int inherited=0; }
    public static class Trap extends BaseTrap {
        public static int shared=999;
        public int camera, speed, toll, bar, extension, fixedSpeed;
        public Trap() {}
    }
    public static final class NonIntTrap extends Trap { public String extra="camera"; }
    public static final class Stock {
        int calls; int[] last; Trap received; boolean fail;
        CountDownLatch entered,release;
        public void legacy(int a,int b,int c,int d,int e,int f,int g,int h,int i,int j,int k,
                int l,int m,int n,int o,int p,int q) {
            Object[] args={a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q};
            if (!ModContext.enter(OLD,this,args)) return;
            if (entered!=null) {
                entered.countDown();
                try { release.await(); } catch(InterruptedException x) { throw new AssertionError(x); }
            }
            if (fail) throw new IllegalStateException("stock failed");
            calls++; last=new int[] {a,b,c,d,e,f,g,h,i,j,k,l,m,n,o,p,q};
        }
        public void modern(Trap t) {
            if (!ModContext.enter(NEW,this,new Object[]{t})) return;
            if (fail) throw new IllegalStateException("stock failed");
            calls++; received=t;
            last=new int[] {t.camera,t.speed,t.toll,t.bar,t.extension,t.inherited};
        }
        public void modern(NonIntTrap t) { throw new AssertionError("unsupported record delivered"); }
    }
    static void check(boolean ok,String message) {
        assertions++; if (!ok) throw new AssertionError(message);
    }
    static Intent message(boolean active,int bar) {
        return new Intent(CpTrapOverlay.ACTION).setPackage("com.mnsoft.navi")
            .putExtra("active",active).putExtra("bar",bar);
    }
    // Camera/trap/toll arguments stay "none" (0) unless a test turns one on.
    static final java.util.List<Integer> CAMERA=Arrays.asList(0,1,2,3,4,5,6,7,9,10,13);
    static int value(int offset,int i) { return CAMERA.contains(i) ? 0 : offset+i; }
    static void legacy(Stock s,int offset) { legacy(s,offset,-1); }
    static void legacy(Stock s,int offset,int camera) {
        int[] v=new int[17];
        for (int i=0;i<17;i++) v[i]=value(offset,i);
        if (camera>=0) v[camera]=60;
        s.legacy(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],v[11],v[12],v[13],v[14],v[15],v[16]);
    }
    static void legacyEquals(Stock s,int offset,int bar) {
        for (int i=0;i<17;i++) check(s.last[i]==(i==11?bar:value(offset,i)),"legacy only bar changed: "+i);
    }
    static Trap trap() {
        Trap t=new Trap();
        t.camera=5;t.speed=70;t.toll=3100;t.bar=26;t.extension=123;t.inherited=888;
        return t;
    }
    public static void main(String[] args) throws Exception {
        final Context ctx=new Context(); ModContext mod=new ModContext();
        CpTrapOverlay.install(mod,new LoadedApp("other","other",null,ctx));
        Points.absent="framework.tbt_trap_info";
        CpTrapOverlay.install(mod,new LoadedApp("com.mnsoft.navi","navigation",null,ctx));
        check(mod.errors==0,"missing optional points turn the bar off without errors");
        Points.absent="";
        Points.recordType=NonIntTrap.class;
        CpTrapOverlay.install(mod,new LoadedApp("com.mnsoft.navi","navigation",null,ctx));
        check(mod.errors==1,"unsupported mapped record fails closed before hooks");
        Points.recordType=Trap.class;
        CpTrapOverlay.install(mod,new LoadedApp("com.mnsoft.navi","navigation",null,ctx));
        CpTrapOverlay.install(mod,new LoadedApp("com.mnsoft.navi","navigation",null,ctx));
        final Stock stock=new Stock(); Points.cluster=stock;
        ctx.sendBroadcast(message(true,80));
        int[] neutral=new int[17]; neutral[2]=3; neutral[11]=80;
        check(stock.calls==1 && Arrays.equals(stock.last,neutral),"no stock record: neutral record carries the bar");
        Handler.advance(3500);
        neutral[11]=0;
        check(stock.calls==2 && Arrays.equals(stock.last,neutral),"expiry restores the neutral bar to 0");
        stock.calls=0;
        legacy(stock,100); legacyEquals(stock,100,111);
        check(stock.calls==1,"own replay guard delivers once");
        ctx.sendBroadcast(message(true,90).setPackage(null));
        ctx.sendBroadcast(message(true,90).setPackage("other"));
        ctx.sendBroadcast(new Intent("other").setPackage("com.mnsoft.navi"));
        ctx.sendBroadcast(message(true,-1)); ctx.sendBroadcast(message(true,101));
        check(stock.calls==1,"implicit, wrong package/action and invalid bar ignored");
        ctx.sendBroadcast(message(true,80)); legacyEquals(stock,100,80);
        check(stock.calls==2,"bar update replays latest stock");
        ctx.sendBroadcast(message(true,80));
        check(stock.calls==2,"same bar only renews lease");
        legacy(stock,200); legacyEquals(stock,200,80);
        check(stock.calls==3,"fresh camera data still delivered");
        ctx.sendBroadcast(message(false,0)); legacyEquals(stock,200,211);
        check(stock.calls==4,"explicit end restores latest original bar");
        ctx.sendBroadcast(message(false,0)); check(stock.calls==4,"repeat end harmless");
        ctx.sendBroadcast(message(true,0)); legacyEquals(stock,200,0);
        Handler.advance(3000); ctx.sendBroadcast(message(true,100)); legacyEquals(stock,200,100);
        Handler.advance(500); legacyEquals(stock,200,100);
        Handler.advance(2999); legacyEquals(stock,200,100);
        Handler.advance(1); legacyEquals(stock,200,211);
        int before=stock.calls; Handler.advance(5000);
        check(stock.calls==before,"expiry restores exactly once");

        Trap t=trap(); stock.modern(t);
        check(stock.received==t,"inactive stock record delivered untouched");
        t.camera=900;t.speed=900;t.toll=900;t.bar=900;t.extension=900;t.inherited=900;
        ctx.sendBroadcast(message(true,65));
        check(Arrays.equals(stock.last,new int[]{5,70,3100,65,123,888}),"immutable snapshot including inherited/unknown int fields");
        check(stock.received!=t,"new object clone");
        check(t.bar==900 && t.camera==900,"never mutates stock object");
        stock.received.camera=999; stock.received.bar=999;
        ctx.sendBroadcast(message(true,55));
        check(Arrays.equals(stock.last,new int[]{5,70,3100,55,123,888}),"replay objects do not alias snapshot");
        Trap fresh=trap();fresh.camera=7;fresh.bar=41;
        stock.modern(fresh);
        check(Arrays.equals(stock.last,new int[]{7,70,3100,55,123,888}),"new camera record while active preserved");
        check(fresh.bar==41,"active incoming object unchanged");
        ctx.sendBroadcast(message(false,0));
        check(Arrays.equals(stock.last,new int[]{7,70,3100,41,123,888}),"object end restores stock bar");
        check(Trap.shared==999,"static fields untouched");
        ctx.sendBroadcast(message(true,45)); Handler.advance(3500);
        check(stock.last[3]==41,"object expiry restores stock bar");
        ctx.sendBroadcast(message(true,45)); legacy(stock,300);
        ctx.sendBroadcast(message(false,0)); legacyEquals(stock,300,311);

        // Expiry and updates cannot overtake an in-flight stock camera update.
        stock.entered=new CountDownLatch(1); stock.release=new CountDownLatch(1);
        final AtomicReference<Throwable> error=new AtomicReference<Throwable>();
        Thread producer=new Thread(new Runnable() { public void run() {
            try { legacy(stock,400); } catch(Throwable t) { error.set(t); }
        }});
        producer.start(); stock.entered.await();
        final CountDownLatch broadcastStarted=new CountDownLatch(1);
        Thread broadcast=new Thread(new Runnable() { public void run() {
            broadcastStarted.countDown();
            try { ctx.sendBroadcast(message(true,33)); } catch(Throwable t) { error.set(t); }
        }});
        broadcast.start(); broadcastStarted.await(); stock.release.countDown();
        producer.join(3000);broadcast.join(3000);
        check(!producer.isAlive()&&!broadcast.isAlive()&&error.get()==null,"serialized stock and broadcast complete");
        legacyEquals(stock,400,33);
        ctx.sendBroadcast(message(false,0)); legacyEquals(stock,400,411);
        stock.entered=null;stock.fail=true;
        try { legacy(stock,500); throw new AssertionError("stock error swallowed"); }
        catch(RuntimeException expected) {}
        stock.fail=false; legacy(stock,600); legacyEquals(stock,600,611);

        // An active camera/trap owns the bar: no override while the stock record reports one.
        for (int camera : new int[] {0,3,5,9,13}) {
            ctx.sendBroadcast(message(true,20)); legacy(stock,700); legacyEquals(stock,700,20);
            legacy(stock,700,camera);
            check(stock.last[11]==711 && stock.last[camera]==60,"camera arg "+camera+" passes stock bar");
            int calls=stock.calls; ctx.sendBroadcast(message(true,10));
            check(stock.calls==calls && stock.last[11]==711,"lease renewal does not override camera bar");
            legacy(stock,700); legacyEquals(stock,700,10);
            ctx.sendBroadcast(message(false,0)); legacyEquals(stock,700,711);
        }
        ctx.sendBroadcast(message(true,20)); legacy(stock,800);
        legacy(stock,800,2); ctx.sendBroadcast(message(false,0));
        check(stock.last[11]==811,"end after camera keeps stock bar");
        Trap cam=trap(); cam.fixedSpeed=60; ctx.sendBroadcast(message(true,20)); stock.modern(cam);
        check(stock.received==cam && cam.bar==26,"object camera record delivered untouched");
        Trap plain=trap(); stock.modern(plain);
        check(stock.last[3]==20,"object overlay resumes without camera");
        ctx.sendBroadcast(message(false,0));
        check(stock.last[3]==26,"object end restores");
        Trap negative=trap(); negative.fixedSpeed=-1; ctx.sendBroadcast(message(true,30)); stock.modern(negative);
        check(stock.last[3]==30,"-1 is no camera");
        ctx.sendBroadcast(message(false,0));
        check(mod.errors==1,"no errors after supported map installation");
        System.out.println("CpTrapOverlay: "+assertions+" assertions passed");
    }
}
'''

with tempfile.TemporaryDirectory(prefix="hmg-seogaemo-trapoverlay-") as temp:
    root = Path(temp)
    sources = dict(STUBS)
    sources["hmgapi/hook/CpTrapOverlayTest.java"] = TEST
    for name in ("MethodHook", "LoadedApp"):
        sources[f"hmgapi/hook/{name}.java"] = (
            MODULE / f"payload/src/hmgapi/hook/{name}.java.src").read_text()
    sources["hmgapi/mods/wireless/CpTrapOverlay.java"] = (
        MODULE / "payload/src/hmgapi/mods/wireless/CpTrapOverlay.java.src").read_text()
    for name, content in sources.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    java = [str(root / name) for name in sources]
    subprocess.run(["javac", "--release", "7", "-Xlint:-options", "-d", str(root / "classes"), *java], check=True)
    subprocess.run(["java", "-cp", str(root / "classes"), "hmgapi.hook.CpTrapOverlayTest"], check=True)
    # Compile the helper against real API 19 as well; retain hook stubs but exclude host-only harness.
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    android = Path(os.environ.get("ANDROID_JAR", sdk / "platforms/android-19/android.jar"))
    if android.is_file():
        stub_classes = root / "api-stubs"
        stub_classes.mkdir()
        # javac sourcepath stays empty; Android symbols must come from the platform bootclasspath.
        subprocess.run([
            "javac", "-source", "1.7", "-target", "1.7", "-Xlint:-options",
            "-bootclasspath", str(android), "-classpath", str(root / "classes"),
            "-sourcepath", str(root / "empty-sourcepath"), "-d", str(stub_classes),
            str(root / "hmgapi/mods/wireless/CpTrapOverlay.java"),
        ], check=True)
        print("CpTrapOverlay: Android API 19 / Java 7 compile passed")
        hook_api = MODULE.parents[1] / "out/hook-sdk/hmg-seogaemo-hook-api.jar"
        if hook_api.is_file():
            subprocess.run([
                "javac", "-source", "1.7", "-target", "1.7", "-Xlint:-options",
                "-bootclasspath", str(android), "-classpath", str(hook_api),
                "-sourcepath", str(root / "empty-sourcepath"), "-d", str(stub_classes),
                str(root / "hmgapi/mods/wireless/CpTrapOverlay.java"),
            ], check=True)
            print("CpTrapOverlay: production hook SDK compile passed")
    else:
        print("CpTrapOverlay: API 19 compile skipped (ANDROID_JAR unavailable)")
