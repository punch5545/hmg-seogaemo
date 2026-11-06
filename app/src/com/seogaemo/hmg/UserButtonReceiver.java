package com.seogaemo.hmg;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Fixed user-button actions; accepts no caller-supplied component or extras. Which app a button
 * opens is the owner's choice stored in this app (default HMG-Seogaemo), never sent to root.
 */
public final class UserButtonReceiver extends BroadcastReceiver {
    public static final String ACTION_HEAD = "com.seogaemo.hmg.USER_BUTTON_HEAD";
    public static final String ACTION_SWRC = "com.seogaemo.hmg.USER_BUTTON_SWRC";
    /** Written by the separate button modules before they were merged; opens HMG-Seogaemo. */
    public static final String ACTION_LEGACY = "com.seogaemo.hmg.USER_BUTTON_SHORTCUT";
    public static final String PREFS = "user_button";

    static String slot(String action) {
        if (ACTION_HEAD.equals(action)) return "head";
        if (ACTION_SWRC.equals(action)) return "swrc";
        return null;
    }

    public static String target(Context context, String slot) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("target_" + slot, "");
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String slot = slot(action);
        if (slot == null && !ACTION_LEGACY.equals(action)) return;
        try {
            String pkg = slot == null ? "" : target(context, slot);
            Intent launch = pkg.length() == 0 ? null : context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) launch = new Intent(context, MainActivity.class).setAction(Intent.ACTION_MAIN);
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Log.e("HMG-Seogaemo", "Unable to open user button shortcut", e);
        }
    }
}
