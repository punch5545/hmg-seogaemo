package com.seogaemo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.seogaemo.app.core.update.UpdateClient;
import com.seogaemo.app.module.ModuleManager;
import com.seogaemo.app.module.Module;
import com.seogaemo.app.module.ModuleRegistry;
import com.seogaemo.app.module.RootClient;
import com.seogaemo.app.module.RootResult;
import java.util.Locale;

/** Gen5W visual mock. No vehicle or settings operations are performed. */
public final class MainActivity extends Activity {
    private static final int CANVAS = 0xFF111111;
    private static final int SURFACE = 0xFF171717;
    private static final int RAISED = 0xFF202020;
    private static final int HAIRLINE = 0xFF343434;
    private static final int WHITE = 0xFFF5F5F5;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int BLUE = 0xFF0070F3;
    private static final int ERROR = 0xFFEE0000;
    private static final int PRESS = 0xFF294563;
    private static final int RED_PRESS = 0xFF633030;
    private static final int[] ACTIONS = {R.string.action_battery, R.string.action_exhaust,
        R.string.action_climate, R.string.action_drive, R.string.action_audio,
        R.string.action_connect, R.string.action_restart, R.string.settings};
    private static final int[] ICONS = {R.string.icon_battery, R.string.icon_exhaust,
        R.string.icon_climate, R.string.icon_drive, R.string.icon_audio,
        R.string.icon_connect, R.string.icon_restart, R.string.icon_settings};
    private static final int[] CATEGORIES = {R.string.cat_general, R.string.cat_wireless,
        R.string.cat_logs, R.string.cat_display, R.string.cat_about};
    private static final int[] CATEGORY_SUBS = {R.string.cat_general_sub, R.string.cat_wireless_sub,
        R.string.cat_logs_sub, R.string.cat_display_sub, R.string.cat_about_sub};
    private Typeface sans;
    private Typeface medium;
    private Typeface mono;
    private int category = 0;
    private final boolean[][] checks = {{false, false}, {true, false}, {true, false}, {true, false}, {false, false}};
    private boolean settingsVisible;
    private TextView updateStatus;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        applyLocale();
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        sans = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        mono = Typeface.MONOSPACE;
        if (state != null && state.getBoolean("settings", false)) {
            category = state.getInt("category", 0);
            showSettings();
        } else showHome();
        if (getSharedPreferences("update", MODE_PRIVATE).getBoolean("resume", false)) {
            try {
                if (new UpdateClient(this).hasUsableNetwork()) {
                    category = 0;
                    showSettings();
                    checkUpdate();
                }
            } catch (Exception ignored) { }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("settings", settingsVisible);
        state.putInt("category", category);
        super.onSaveInstanceState(state);
    }

    private SharedPreferences preferences() {
        return getSharedPreferences("ui_language", MODE_PRIVATE);
    }

    private void applyLocale() {
        String choice = preferences().getString("language", "system");
        Locale selected = "system".equals(choice) ? Resources.getSystem().getConfiguration().locale
                : new Locale(choice);
        Locale.setDefault(selected);
        Configuration config = new Configuration(getResources().getConfiguration());
        config.locale = selected;
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private GradientDrawable shape(int fill, int border, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) d.setStroke(dp(1), border);
        return d;
    }

    private StateListDrawable selectable(int fill, int border, int radius, boolean destructive) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_pressed}, shape(destructive ? RED_PRESS : PRESS,
                destructive ? ERROR : BLUE, radius));
        d.addState(new int[] {}, shape(fill, border, radius));
        return d;
    }

    private TextView text(String label, float sp, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout horizontal() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private LinearLayout root() {
        LinearLayout r = vertical();
        r.setBackgroundColor(CANVAS);
        setContentView(r);
        return r;
    }

    private void divider(LinearLayout parent, boolean vertical) {
        View d = new View(this);
        d.setBackgroundColor(HAIRLINE);
        parent.addView(d, new LinearLayout.LayoutParams(vertical ? dp(1) : -1, vertical ? -1 : dp(1)));
    }

    private final class BrandMark extends View {
        private final Paint paint = new Paint();
        BrandMark() { super(MainActivity.this); paint.setColor(0xFF101016); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.WHITE);
            float unit = getWidth() / 300f;
            canvas.drawRect(70 * unit, 70 * unit, 176 * unit, 176 * unit, paint);
            canvas.drawRect(124 * unit, 124 * unit, 230 * unit, 230 * unit, paint);
        }
    }

    private void header(LinearLayout root, boolean back) {
        LinearLayout bar = horizontal();
        bar.setBackgroundColor(SURFACE);
        View inset = new View(this);
        bar.addView(inset, new LinearLayout.LayoutParams(dp(22), dp(60)));
        bar.addView(new BrandMark(), new LinearLayout.LayoutParams(dp(46), dp(46)));
        View gap = new View(this);
        bar.addView(gap, new LinearLayout.LayoutParams(dp(12), dp(60)));
        TextView brand = text(getString(R.string.app_name), 28, WHITE, medium);
        brand.setTextScaleX(0.97f);
        bar.addView(brand, new LinearLayout.LayoutParams(dp(256), dp(60)));
        if (back) {
            TextView section = text(getString(R.string.settings), 22, MUTED, sans);
            section.setPadding(dp(16), 0, 0, 0);
            bar.addView(section, new LinearLayout.LayoutParams(dp(130), dp(60)));
        }
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, dp(60), 1));
        TextView meta = text(getString(R.string.meta), 16, MUTED, mono);
        meta.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bar.addView(meta, new LinearLayout.LayoutParams(dp(270), dp(60)));
        if (back) {
            TextView backButton = text(getString(R.string.nav_back_label), 21, WHITE, medium);
            backButton.setGravity(Gravity.CENTER);
            backButton.setContentDescription(getString(R.string.back));
            backButton.setBackgroundDrawable(selectable(SURFACE, Color.TRANSPARENT, 0, false));
            backButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showHome(); }
            });
            bar.addView(backButton, new LinearLayout.LayoutParams(dp(132), dp(60)));
        } else {
            TextView close = text(getString(R.string.nav_close_glyph), 35, MUTED, sans);
            close.setGravity(Gravity.CENTER);
            close.setContentDescription(getString(R.string.close));
            close.setBackgroundDrawable(selectable(SURFACE, Color.TRANSPARENT, 0, false));
            close.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { close(); }
            });
            bar.addView(close, new LinearLayout.LayoutParams(dp(72), dp(60)));
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(60)));
        divider(root, false);
    }

    private void showHome() {
        settingsVisible = false;
        LinearLayout r = root();
        header(r, false);
        LinearLayout body = vertical();
        body.setGravity(Gravity.CENTER);
        r.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout intro = horizontal();
        TextView title = text(getString(R.string.home_title), 26, WHITE, medium);
        title.setTextScaleX(0.97f);
        intro.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView note = text(getString(R.string.placeholder_note), 15, MUTED, mono);
        note.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        intro.addView(note, new LinearLayout.LayoutParams(-2, dp(42)));
        body.addView(intro, new LinearLayout.LayoutParams(dp(840), dp(42)));
        for (int row = 0; row < 2; row++) {
            LinearLayout strip = horizontal();
            for (int col = 0; col < 4; col++) {
                int index = row * 4 + col;
                FrameLayout tile = makeAction(index);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(204), dp(166));
                if (col > 0) p.leftMargin = dp(8);
                strip.addView(tile, p);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, dp(166));
            if (row > 0) rp.topMargin = dp(8);
            body.addView(strip, rp);
        }
    }

    private FrameLayout makeAction(final int index) {
        boolean destructive = index == 6;
        FrameLayout tile = new FrameLayout(this);
        tile.setBackgroundDrawable(selectable(destructive ? 0xFF241919 : RAISED,
                destructive ? 0xFF743333 : HAIRLINE, 8, destructive));
        tile.setClickable(true);
        TextView icon = text(getString(ICONS[index]), 20, destructive ? ERROR : index == 7 ? BLUE : WHITE, mono);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundDrawable(shape(SURFACE, destructive ? ERROR : index == 7 ? BLUE : HAIRLINE, 8));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(66), dp(66), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ip.topMargin = dp(22);
        tile.addView(icon, ip);
        TextView label = text(getString(ACTIONS[index]), index == 0 ? 21 : 24, destructive ? 0xFFFF8888 : WHITE, medium);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(false);
        label.setMaxLines(2);
        label.setTextScaleX(0.97f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(66), Gravity.TOP);
        lp.topMargin = dp(92);
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(6);
        tile.addView(label, lp);
        tile.setContentDescription(getString(ACTIONS[index]));
        if (index == 7) tile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showSettings(); }
        });
        return tile;
    }

    private void showSettings() {
        settingsVisible = true;
        LinearLayout r = root();
        header(r, true);
        LinearLayout body = horizontal();
        r.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout side = vertical();
        side.setBackgroundColor(SURFACE);
        body.addView(side, new LinearLayout.LayoutParams(0, -1, 1));
        TextView sideTitle = text(getString(R.string.categories), 17, MUTED, mono);
        sideTitle.setPadding(dp(22), 0, 0, 0);
        side.addView(sideTitle, new LinearLayout.LayoutParams(-1, dp(52)));
        for (int i = 0; i < CATEGORIES.length; i++) addCategory(side, i);
        divider(body, true);
        LinearLayout content = vertical();
        body.addView(content, new LinearLayout.LayoutParams(0, -1, 2));
        TextView paneTitle = text(getString(CATEGORIES[category]), 26, WHITE, medium);
        paneTitle.setPadding(dp(30), 0, 0, 0);
        paneTitle.setTextScaleX(0.97f);
        content.addView(paneTitle, new LinearLayout.LayoutParams(-1, dp(52)));
        divider(content, false);
        if (category == 0) {
            ScrollView scroll = new ScrollView(this);
            LinearLayout general = vertical();
            scroll.addView(general);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            showLanguage(general);
            showUpdate(general);
            return;
        } else if (category == 1) {
            addRow(content, R.string.carplay, R.string.wireless_hint, 0, 0);
            addRow(content, R.string.android_auto, R.string.wireless_hint, 0, 1);
            addRow(content, R.string.connection_mode, R.string.automatic_pairing, R.string.value_auto, -1);
            addRow(content, R.string.profile, R.string.target_head_unit, R.string.value_gen5w, -1);
        } else if (category == 2) {
            addRow(content, R.string.diagnostic_logging, R.string.record_events, 0, 0);
            addRow(content, R.string.verbose_output, R.string.detailed_entries, 0, 1);
            addRow(content, R.string.last_export, R.string.no_logs, R.string.value_never, -1);
            addRow(content, R.string.storage, R.string.local_log_space, R.string.value_zero_mb, -1);
        } else if (category == 3) {
            addRow(content, R.string.high_contrast, R.string.legibility, 0, 0);
            addRow(content, R.string.night_theme, R.string.dark_display, 0, 1);
            addRow(content, R.string.resolution, R.string.wide_head_unit, R.string.value_resolution, -1);
            addRow(content, R.string.density, R.string.display_scaling, R.string.value_dpi, -1);
        } else {
            addRow(content, R.string.app, R.string.ui_mock, R.string.app_name, -1);
            addRow(content, R.string.version, R.string.preview_build, installedVersion(), -1);
            addRow(content, R.string.android, R.string.minimum_api, R.string.value_api, -1);
            addRow(content, R.string.platform, R.string.vehicle_brands, R.string.value_gen5w, -1);
        }
        LinearLayout actionBar = horizontal();
        actionBar.setPadding(dp(30), 0, dp(28), 0);
        View spacer = new View(this);
        actionBar.addView(spacer, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView secondary = action(getString(R.string.cancel), false);
        actionBar.addView(secondary, new LinearLayout.LayoutParams(dp(142), dp(60)));
        TextView primary = action(getString(category == 2 ? R.string.export_logs : R.string.apply), true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(170), dp(60));
        pp.leftMargin = dp(10);
        actionBar.addView(primary, pp);
        content.addView(actionBar, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showLanguage(LinearLayout content) {
        TextView label = text(getString(R.string.language), 24, WHITE, medium);
        label.setPadding(dp(30), 0, 0, 0);
        content.addView(label, new LinearLayout.LayoutParams(-1, dp(48)));
        addLanguageOption(content, R.string.language_system, "system");
        addLanguageOption(content, R.string.language_english, "en");
        addLanguageOption(content, R.string.language_korean, "ko");
        TextView hint = text(getString(R.string.language_scope), 18, MUTED, sans);
        hint.setPadding(dp(30), 0, 0, 0);
        content.addView(hint, new LinearLayout.LayoutParams(-1, dp(40)));
    }

    private void addLanguageOption(LinearLayout content, int labelRes, final String choice) {
        boolean selected = choice.equals(preferences().getString("language", "system"));
        LinearLayout row = horizontal();
        row.setPadding(dp(30), 0, dp(28), 0);
        row.setBackgroundDrawable(selectable(selected ? 0xFF1C2938 : CANVAS,
                Color.TRANSPARENT, 0, false));
        TextView title = text(getString(labelRes), 24, WHITE, medium);
        row.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        TextView radio = text(getString(selected ? R.string.radio_selected : R.string.radio_unselected), 28, selected ? BLUE : MUTED, sans);
        radio.setGravity(Gravity.CENTER);
        row.addView(radio, new LinearLayout.LayoutParams(dp(60), -1));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                preferences().edit().putString("language", choice).apply();
                recreate();
            }
        });
        content.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
        divider(content, false);
    }

    private void showUpdate(LinearLayout content) {
        addRow(content, R.string.update_current, R.string.update_current_hint, installedVersion(), -1);
        updateStatus = text(getString(R.string.update_idle), 18, MUTED, sans);
        updateStatus.setPadding(dp(30), 0, dp(28), 0);
        content.addView(updateStatus, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView check = action(getString(R.string.update_check), true);
        check.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { checkUpdate(); } });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(60));
        cp.leftMargin = dp(30); cp.rightMargin = dp(28);
        content.addView(check, cp);
        TextView rollback = action(getString(R.string.update_rollback), false);
        rollback.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmRollback(); } });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(60));
        rp.leftMargin = dp(30); rp.rightMargin = dp(28); rp.topMargin = dp(10);
        content.addView(rollback, rp);
    }

    private void updateMessage(final String value) {
        runOnUiThread(new Runnable() { public void run() { if (updateStatus != null) updateStatus.setText(value); } });
    }

    private void checkUpdate() {
        try {
            final UpdateClient client = new UpdateClient(this);
            if (!client.hasUsableNetwork()) {
                new AlertDialog.Builder(this).setMessage(R.string.update_wifi_required)
                    .setPositiveButton(R.string.update_enable_wifi, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            new Thread(new Runnable() { public void run() {
                                String prior = enabledModules();
                                getSharedPreferences("update", MODE_PRIVATE).edit().putString("restore", prior).putBoolean("resume", true).apply();
                                RootResult result = new ModuleManager().requestEnable("wifi-client", "update-check");
                                if (result.exitCode != 0) getSharedPreferences("update", MODE_PRIVATE).edit().clear().apply();
                                updateMessage(result.exitCode == 0 ? getString(R.string.update_resume_after_restart) : result.detail);
                            }}, "seogaemo-update-wifi").start();
                        }
                    }).setNegativeButton(android.R.string.cancel, null).show();
                return;
            }
            new Thread(new Runnable() { public void run() {
                try {
                    final String version = client.download(installedVersion(), new UpdateClient.Progress() {
                        public void changed(int phase) { updateMessage(getString(phase)); }
                    });
                    getSharedPreferences("update", MODE_PRIVATE).edit().putBoolean("resume", false).apply();
                    runOnUiThread(new Runnable() { public void run() { confirmInstall(version); } });
                } catch (UpdateClient.NoUpdateException e) { updateMessage(getString(R.string.update_no_update)); restoreModules(); }
                  catch (Exception e) { updateMessage(e.getMessage()); restoreModules(); }
            }}, "seogaemo-update-check").start();
        } catch (Exception e) { updateMessage(e.getMessage()); }
    }

    private void confirmInstall(final String version) {
        new AlertDialog.Builder(this).setMessage(getString(R.string.update_available, version))
            .setPositiveButton(R.string.update_install, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { rootUpdate(true); }
            }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { new Thread(new Runnable() { public void run() { restoreModules(); } }).start(); }
            }).show();
    }

    private void confirmRollback() {
        new AlertDialog.Builder(this).setMessage(R.string.update_rollback_confirm)
            .setPositiveButton(R.string.update_rollback, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { rootUpdate(false); }
            }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void rootUpdate(final boolean install) {
        new Thread(new Runnable() { public void run() {
            try {
                if (install) { updateMessage(getString(R.string.update_staging)); UpdateClient.root("stage"); }
                updateMessage(getString(install ? R.string.update_applying : R.string.update_rolling_back));
                UpdateClient.root(install ? "apply" : "rollback");
                restoreModules();
                updateMessage(getString(R.string.update_complete));
            } catch (Exception e) { updateMessage(e.getMessage()); restoreModules(); }
        }}, "seogaemo-update-root").start();
    }

    private String enabledModules() {
        StringBuilder enabled = new StringBuilder();
        for (Module module : ModuleRegistry.all()) {
            RootResult result = RootClient.run(module.id(), "status");
            if ("on".equals(result.state) || "pending-restart".equals(result.state)) enabled.append(module.id()).append(',');
        }
        return enabled.toString();
    }

    private void restoreModules() {
        SharedPreferences pending = getSharedPreferences("update", MODE_PRIVATE);
        if (!pending.contains("restore")) return;
        String desired = pending.getString("restore", "");
        ModuleManager manager = new ModuleManager();
        boolean success = true;
        for (Module module : ModuleRegistry.all()) {
            boolean wanted = ("," + desired).contains("," + module.id() + ",");
            String observed = RootClient.run(module.id(), "status").state;
            boolean active = "on".equals(observed) || "pending-restart".equals(observed);
            if (active && !wanted && manager.execute(manager.plan(module.id(), false)).exitCode != 0) success = false;
        }
        for (Module module : ModuleRegistry.all()) {
            boolean wanted = ("," + desired).contains("," + module.id() + ",");
            String observed = RootClient.run(module.id(), "status").state;
            boolean active = "on".equals(observed) || "pending-restart".equals(observed);
            if (wanted && !active && manager.execute(manager.plan(module.id(), true)).exitCode != 0) success = false;
        }
        if (success) pending.edit().clear().apply();
        else updateMessage(getString(R.string.update_restore_failed));
    }

    private void addCategory(LinearLayout parent, final int index) {
        LinearLayout row = horizontal();
        row.setPadding(dp(22), 0, dp(16), 0);
        row.setBackgroundDrawable(selectable(index == category ? 0xFF1C2938 : SURFACE,
                Color.TRANSPARENT, 0, false));
        View marker = new View(this);
        marker.setBackgroundColor(index == category ? BLUE : Color.TRANSPARENT);
        row.addView(marker, new LinearLayout.LayoutParams(dp(4), dp(56)));
        LinearLayout copy = vertical();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(16), 0, 0, 0);
        TextView name = text(getString(CATEGORIES[index]), 23, index == category ? WHITE : 0xFFD0D0D0, medium);
        name.setTextScaleX(0.98f);
        TextView sub = text(getString(CATEGORY_SUBS[index]), 17, MUTED, sans);
        LinearLayout.LayoutParams subp = new LinearLayout.LayoutParams(-1, -2);
        subp.topMargin = dp(4);
        copy.addView(name);
        copy.addView(sub, subp);
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { category = index; showSettings(); }
        });
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(70)));
        divider(parent, false);
    }

    private String installedVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void addRow(LinearLayout parent, int label, int sub, int value, final int toggleIndex) {
        addRow(parent, label, sub, value == 0 ? "" : getString(value), toggleIndex);
    }

    private void addRow(LinearLayout parent, int label, int sub, String value, final int toggleIndex) {
        LinearLayout row = horizontal();
        row.setPadding(dp(30), 0, dp(28), 0);
        row.setBackgroundDrawable(selectable(CANVAS, Color.TRANSPARENT, 0, false));
        LinearLayout copy = vertical();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(getString(label), 24, WHITE, medium);
        title.setTextScaleX(0.98f);
        TextView detail = text(getString(sub), 18, MUTED, sans);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(3);
        copy.addView(title);
        copy.addView(detail, sp);
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        if (toggleIndex >= 0) {
            final TextView check = text(getString(checks[category][toggleIndex] ? R.string.on : R.string.off), 18,
                    checks[category][toggleIndex] ? BLUE : MUTED, mono);
            check.setGravity(Gravity.CENTER);
            updateCheck(check, checks[category][toggleIndex]);
            row.addView(check, new LinearLayout.LayoutParams(dp(96), dp(48)));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    checks[category][toggleIndex] = !checks[category][toggleIndex];
                    updateCheck(check, checks[category][toggleIndex]);
                }
            });
        } else {
            TextView trailing = text(value, 20, WHITE, mono);
            trailing.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            row.addView(trailing, new LinearLayout.LayoutParams(dp(200), -1));
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(75)));
        divider(parent, false);
    }

    private void updateCheck(TextView check, boolean on) {
        check.setText(getString(on ? R.string.on : R.string.off));
        check.setTextColor(on ? BLUE : MUTED);
        check.setBackgroundDrawable(shape(on ? 0xFF17283A : RAISED, on ? BLUE : HAIRLINE, 6));
    }

    private TextView action(String label, boolean primary) {
        TextView b = text(label, 21, WHITE, medium);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundDrawable(selectable(primary ? BLUE : RAISED,
                primary ? BLUE : HAIRLINE, 6, false));
        b.setClickable(true);
        return b;
    }

    /** HMG's finishActivity() may reject a missing GET_TASKS grant. */
    private void close() {
        try { finish(); }
        catch (SecurityException denied) {
            try { moveTaskToBack(true); }
            catch (Throwable ignored) { }
        }
    }

    @Override public void onBackPressed() { showHome(); }
}
