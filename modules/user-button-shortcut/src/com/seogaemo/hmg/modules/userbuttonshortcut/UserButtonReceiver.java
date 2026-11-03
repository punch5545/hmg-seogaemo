package com.seogaemo.hmg.modules.userbuttonshortcut;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.seogaemo.hmg.MainActivity;

/** Fixed navigation action; accepts no caller-supplied component or extras. */
public final class UserButtonReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.seogaemo.hmg.USER_BUTTON_SHORTCUT";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        try {
            context.startActivity(new Intent(context, MainActivity.class)
                    .setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Log.e("HMG-Seogaemo", "Unable to open user button shortcut", e);
        }
    }
}
