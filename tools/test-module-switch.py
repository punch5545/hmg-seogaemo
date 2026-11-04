#!/usr/bin/env python3
"""Java plan/execution/resume regressions against an isolated real dispatcher."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
from testlib import car_env, copy_shell_script, SHELL

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--dispatcher', type=Path, default=ROOT / 'nemesis/package/bin/hmg-seogaemo-root')
args = parser.parse_args()
# Verification may outlast launch/resume and the initial network broadcast.
activity = (ROOT / 'app/src/com/seogaemo/hmg/MainActivity.java').read_text()
refresh = activity.split('private void refreshMap(', 1)[1].split('private void confirmMapRun(', 1)[0]
assert refresh.index('mapRefreshing = false;') < refresh.index('resumeUpdateWhenReady();')
resume = activity.split('private void resumeUpdateWhenReady(', 1)[1].split('private SharedPreferences', 1)[0]
assert 'mapRefreshing ||' in resume
SOURCES = {
'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 Editor edit(); interface Editor { Editor putString(String k,String v); Editor clear(); boolean commit(); }
}''',
'android/content/Context.java': '''package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 public final Map<String,String> saved=new HashMap<String,String>();
 public SharedPreferences getSharedPreferences(String n,int mode) {return new SharedPreferences(){
  public Editor edit(){return new Editor(){
   public Editor putString(String k,String v){saved.put(k,v);return this;}
   public Editor clear(){saved.clear();return this;} public boolean commit(){return true;}
  };}
 };}
}''',
'com/seogaemo/hmg/module/ModuleRegistry.java': '''package com.seogaemo.hmg.module;
public class ModuleRegistry {
 public static String[] orderedIds(){return new String[]{"wifi-client","wireless-carplay-and-aa"};}
 public static String[] depends(String id){for(String s:orderedIds())if(s.equals(id))return new String[0];throw new IllegalArgumentException(id);}
 public static String[] excludes(String id){return new String[]{id.equals("wifi-client")?"wireless-carplay-and-aa":"wifi-client"};}
}''',
'com/seogaemo/hmg/module/RootClient.java': '''package com.seogaemo.hmg.module;
import java.util.*;import java.io.*;
public class RootClient {
 public static boolean fixture; public static int operation; public static RootResult next;
 public static RootResult status(String state,String restart,String desired,int exit){
  Map<String,String> values=new HashMap<String,String>();values.put("info.desired",desired);
  return new RootResult(state,restart,"test",exit,values);
 }
 public static RootResult run(String id,String verb){
  if(fixture){if(verb.equals("status"))return status("off","none","on",0);operation++;return next;}
  try{
   Process p=new ProcessBuilder(System.getenv("TEST_SHELL"),System.getenv("TEST_DISPATCHER"),id,verb).redirectErrorStream(true).start();
   Map<String,String> values=new HashMap<String,String>();String state="failed",restart="none",detail="";
   BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),"US-ASCII"));String line;
   while((line=r.readLine())!=null){int eq=line.indexOf('=');if(eq<1)continue;String k=line.substring(0,eq),v=line.substring(eq+1);
    if(k.equals("state"))state=v;else if(k.equals("restart"))restart=v;else if(k.equals("detail"))detail=v;else values.put(k,v);}
   return new RootResult(state,restart,detail,p.waitFor(),values);
  }catch(Exception e){throw new RuntimeException(e);}
 }
}''',
'SwitchTest.java': '''import com.seogaemo.hmg.module.*;
import java.util.*;import java.io.*;
public class SwitchTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void plan(ModulePlan p,String expected){String s="";for(ModulePlan.Step step:p.steps)s+=(step.enable?"+":"-")+step.id+" ";check(s.equals(expected),s+" != "+expected);}
 static void write(String path,String value)throws Exception{FileWriter w=new FileWriter(path);w.write(value);w.close();}
 public static void main(String[] args)throws Exception{
  String[] order={"base","dependent","other"};
  Map<String,String[]> deps=new HashMap<String,String[]>(),exc=new HashMap<String,String[]>();
  deps.put("base",new String[0]);deps.put("dependent",new String[]{"base"});deps.put("other",new String[0]);
  exc.put("base",new String[]{"other"});exc.put("dependent",new String[0]);exc.put("other",new String[]{"base"});
  Set<String> none=new HashSet<String>(),desired=new HashSet<String>(Arrays.asList("base","dependent"));
  plan(ModulePlanner.plan("other",true,order,deps,exc,none,desired),"-dependent -base +other ");
  plan(ModulePlanner.plan("base",false,order,deps,exc,none,desired),"-dependent -base ");
  plan(ModulePlanner.plan("base",true,order,deps,exc,none,desired),"+base ");
  plan(ModulePlanner.plan("base",true,order,deps,exc,desired,none),"+base ");
  ModuleManager manager=new ModuleManager();
  RootResult pending=RootClient.status("pending-restart","head_unit","off",0);
  RootClient.fixture=true;RootClient.next=pending;
  ModulePlan steps=new ModulePlan(Arrays.asList(new ModulePlan.Step("wifi-client",false),new ModulePlan.Step("wireless-carplay-and-aa",true)),Collections.<Warning>emptyList());
  RootResult result=manager.execute(steps);
  check(RootClient.operation==2,"pending success stopped execution");
  check(result.outcomes.size()==2 && result.restart.equals("head_unit"),"missing outcomes/restart");
  try{result.outcomes.clear();throw new AssertionError("mutable outcomes");}catch(UnsupportedOperationException expected){}
  RootClient.next=RootClient.status("on","adbd","on",0);
  check(manager.execute(steps).restart.equals("adbd"),"ADB restart lost");
  RootResult failure=RootClient.status("conflict","none","off",3);
  RootClient.next=failure;RootClient.operation=0;
  check(manager.execute(steps).exitCode==3 && RootClient.operation==1,"continued after failure");
  result=RootResult.combined(failure,"head_unit",result.outcomes);
  check(result.exitCode==3 && result.state.equals("conflict") && result.restart.equals("head_unit"),"failure hid prior restart");
  check(result.info.get("desired").equals("off"),"info key prefix corrupted");
  check(!ModuleResume.ready(pending,true),"resumed before reboot");
  RootResult ready=RootClient.status("on","none","on",0);
  check(!ModuleResume.ready(ready,false),"resumed without network");
  check(ModuleResume.ready(ready,true),"did not resume after network");
  check(!ModuleResume.restarted("boot-a","boot-a"),"same boot counted as reboot");
  check(!ModuleResume.restarted("","boot-b"),"unknown boot counted as reboot");
  check(ModuleResume.restarted("boot-a","boot-b"),"new boot not detected");
  check(!ModuleResume.ready(RootClient.status("off","none","on",0),true),"resumed drifted module");
  check(!ModuleResume.ready(RootClient.status("on","none","off",0),true),"resumed disabled intent");
  android.content.Context context=new android.content.Context();RootClient.next=RootClient.status("pending-restart","head_unit","on",0);
  new ModuleManager(context).requestEnable("wifi-client","update-check");
  check("update-check".equals(context.saved.get("intent")),"pending restart lost resume preference");
  RootClient.next=failure;new ModuleManager(context).requestEnable("wifi-client","update-check");
  check(context.saved.isEmpty(),"failure left runnable resume");
  RootClient.fixture=false;
  String data=System.getenv("SG_DATA_ROOT");
  RootResult stale=RootClient.run("wireless-carplay-and-aa","status");
  check(stale.state.equals("off") && stale.desiredEnabled(),"dispatcher missing desired=on on OFF status: "+stale.info);
  ModulePlan actual=manager.plan("wifi-client",true);
  plan(actual,"-wireless-carplay-and-aa +wifi-client ");
  result=manager.execute(actual);
  check(result.exitCode==0 && result.restart.equals("head_unit") && result.outcomes.size()==2,"real pending revert prevented Wi-Fi apply");
  check(result.outcomes.get(0).result.state.equals("pending-restart"),"pending revert fixture not exercised");
  check(!RootClient.run("wireless-carplay-and-aa","status").desiredEnabled(),"CarPlay desired ON survived switch");
  check(RootClient.run("wifi-client","status").desiredEnabled(),"Wi-Fi desired ON not saved");
  check(!new java.io.File(data+"/stock/paused").exists(),"explicit Wi-Fi apply did not resume paused dispatch");
  write(data+"/desired/wifi-client","off");write(data+"/desired/wireless-carplay-and-aa","on");
  write(data+"/fail-wifi","1");
  result=manager.execute(manager.plan("wifi-client",true));
  check(result.exitCode==3 && result.state.equals("conflict") && result.restart.equals("head_unit"),"real later failure lost restart");
  check(result.outcomes.size()==2 && result.outcomes.get(0).result.exitCode==0,"lost successful revert receipt");
  System.out.println("module switch/resume Java + real dispatcher: OK");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='hmg-seogaemo-switch-') as folder:
    temp = Path(folder)
    for name, content in SOURCES.items():
        target = temp / 'src' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
    base = temp / 'package'
    for part in ('bin', 'lib', 'modules', 'state/desired', 'state/stock'):
        (base / part).mkdir(parents=True)
    (base / 'state/stock/paused').write_text('failed stock restore')
    copy_shell_script(args.dispatcher, base / 'bin/hmg-seogaemo-root')
    copy_shell_script(ROOT / 'nemesis/package/lib/common.sh', base / 'lib/common.sh')
    (base / 'modules.list').write_text('wifi-client\nwireless-carplay-and-aa\n')
    (base / 'modules.graph').write_text(
        'wifi-client\tdepends=\texcludes=wireless-carplay-and-aa\n'
        'wireless-carplay-and-aa\tdepends=\texcludes=wifi-client\n')
    for module in ('wifi-client', 'wireless-carplay-and-aa'):
        (base / 'modules' / (module + '.properties')).write_text('id=' + module + '\n')
        (base / 'modules' / (module + '.sh')).write_text('''case "$1" in
status) echo state=off; echo restart=none;;
revert) echo state=pending-restart; echo restart=head_unit; echo info.desired=off;;
apply) if [ -f "$SG_DATA_ROOT/fail-wifi" ]; then
 echo state=conflict; echo restart=none; echo detail=fixture-conflict; exit 3
 fi
 echo state=on; echo restart=none;;
esac
''')
    (base / 'state/desired/wireless-carplay-and-aa').write_text('on')
    (base / 'state/desired/wifi-client').write_text('off')
    classes = temp / 'classes'
    classes.mkdir()
    java = ROOT / 'app/src/com/seogaemo/hmg/module'
    sources = [str(p) for p in (temp / 'src').rglob('*.java')]
    sources += [str(java / (name + '.java')) for name in (
        'ModuleManager', 'ModulePlan', 'ModulePlanner', 'RootResult', 'ModuleResume', 'Warning')]
    subprocess.run(['javac', '-source', '7', '-target', '7', '-d', str(classes), *sources], check=True)
    env = car_env(dict(os.environ, SG_DATA_ROOT=str(base / 'state'),
                       TEST_SHELL=SHELL, TEST_DISPATCHER=str(base / 'bin/hmg-seogaemo-root')))
    subprocess.run(['java', '-cp', str(classes), 'SwitchTest'], env=env, check=True)
