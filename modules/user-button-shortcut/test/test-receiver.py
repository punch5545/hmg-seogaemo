"""Compile receiver against tiny API stubs and check fixed navigation routing."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
M = Path(__file__).resolve().parents[1]
R = M.parents[1]
source = R / 'app/src/com/seogaemo/hmg/UserButtonReceiver.java'
ns = '{http://schemas.android.com/apk/res/android}'
receiver = ET.parse(R / 'app/AndroidManifest.xml').find(".//receiver")
assert receiver.attrib[ns + 'exported'] == 'true'
action = receiver.find('intent-filter/action').attrib[ns + 'name']
assert action == 'com.seogaemo.hmg.USER_BUTTON_SHORTCUT'
with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    files = {
        'android/content/BroadcastReceiver.java': 'package android.content; public abstract class BroadcastReceiver { public abstract void onReceive(Context c, Intent i); }',
        'android/content/Context.java': 'package android.content; public class Context { public Intent started; public boolean fail; public void startActivity(Intent i) { if (fail) throw new SecurityException(); started=i; } }',
        'android/content/Intent.java': '''package android.content; public class Intent {
            public static final String ACTION_MAIN="android.intent.action.MAIN";
            public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000;
            public String action; public Class<?> target; public int flags;
            public Intent(String a) { action=a; }
            public Intent(Context c, Class<?> t) { target=t; }
            public String getAction() { return action; }
            public Intent setAction(String a) { action=a; return this; }
            public Intent addFlags(int f) { flags|=f; return this; }
        }''',
        'android/util/Log.java': 'package android.util; public class Log { public static int e(String a,String b,Throwable t) { return 0; } }',
        'com/seogaemo/hmg/MainActivity.java': 'package com.seogaemo.hmg; public class MainActivity {}',
        'Check.java': '''import android.content.*; import com.seogaemo.hmg.UserButtonReceiver;
            public class Check { public static void main(String[] args) {
                Context c=new Context(); UserButtonReceiver r=new UserButtonReceiver();
                r.onReceive(c,null); r.onReceive(c,new Intent("other"));
                if(c.started!=null) throw new AssertionError();
                r.onReceive(c,new Intent(UserButtonReceiver.ACTION));
                if(c.started.target!=com.seogaemo.hmg.MainActivity.class ||
                   !Intent.ACTION_MAIN.equals(c.started.action) ||
                   c.started.flags!=Intent.FLAG_ACTIVITY_NEW_TASK) throw new AssertionError();
                c.fail=true; r.onReceive(c,new Intent(UserButtonReceiver.ACTION));
            }}'''
    }
    for name, text in files.items():
        p=root / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text)
    subprocess.run(['javac', '-source', '7', '-target', '7', '-d', str(root), str(source)] + [str(root / n) for n in files], check=True, capture_output=True)
    subprocess.run(['java', '-cp', str(root), 'Check'], check=True)
print('user-button-shortcut receiver tests passed')
