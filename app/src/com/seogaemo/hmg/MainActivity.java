package com.seogaemo.hmg;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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
import com.seogaemo.hmg.module.InfoRow;
import com.seogaemo.hmg.module.Module;
import com.seogaemo.hmg.module.ModuleAction;
import com.seogaemo.hmg.module.ModuleManager;
import com.seogaemo.hmg.module.ModulePlan;
import com.seogaemo.hmg.module.ModuleRegistry;
import com.seogaemo.hmg.module.RootClient;
import com.seogaemo.hmg.module.RootResult;
import com.seogaemo.hmg.module.Warning;
import com.seogaemo.hmg.ui.settings.SettingsPage;
import java.util.ArrayList;
import java.util.List;
import com.seogaemo.hmg.ui.HomeView;
import com.seogaemo.hmg.core.update.UpdateClient;
import java.util.Locale;

/** Gen5W settings and built-in module controls. */
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
    private static final int[] CATEGORIES = {R.string.cat_general, R.string.cat_modules,
        R.string.cat_logs, R.string.cat_about};
    private static final int[] CATEGORY_SUBS = {R.string.cat_general_sub, R.string.cat_modules_sub,
        R.string.cat_logs_sub, R.string.cat_about_sub};
    private Typeface sans;
    private Typeface medium;
    private Typeface mono;
    private int category = 0;
    private int sidebarOffset;
    private ScrollView sidebarScroll;
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
            sidebarOffset = state.getInt("sidebarOffset", 0);
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
        } else if (getSharedPreferences("update", MODE_PRIVATE).contains("restore")) {
            new Thread(new Runnable() { public void run() { if (!restoreModules()) updateMessage(getString(R.string.update_restore_pending)); } }, "seogaemo-update-restore").start();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("settings", settingsVisible);
        state.putInt("category", category);
        state.putInt("sidebarOffset", sidebarScroll == null ? sidebarOffset : sidebarScroll.getScrollY());
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
        r.addView(new HomeView(this, new Runnable() {
            public void run() { showSettings(); }
        }), new LinearLayout.LayoutParams(-1, 0, 1));
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
        if (sidebarScroll != null) sidebarOffset = sidebarScroll.getScrollY();
        settingsVisible = true;
        LinearLayout r = root();
        header(r, true);
        LinearLayout body = horizontal();
        r.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView sideScroll = new ScrollView(this);
        sidebarScroll = sideScroll;
        LinearLayout side = vertical();
        side.setBackgroundColor(SURFACE);
        sideScroll.addView(side);
        body.addView(sideScroll, new LinearLayout.LayoutParams(0, -1, 1));
        TextView sideTitle = text(getString(R.string.categories), 17, MUTED, mono);
        sideTitle.setPadding(dp(22), 0, 0, 0);
        side.addView(sideTitle, new LinearLayout.LayoutParams(-1, dp(52)));
        for (int i = 0; i < CATEGORIES.length; i++) addCategory(side, i);
        divider(side, false);
        for (int i = 0; i < ModuleRegistry.all().length; i++)
            addModuleCategory(side, i, ModuleRegistry.all()[i]);
        sideScroll.post(new Runnable() {
            public void run() { if (sidebarScroll != null) sidebarScroll.scrollTo(0, sidebarOffset); }
        });
        divider(body, true);
        LinearLayout content = vertical();
        body.addView(content, new LinearLayout.LayoutParams(0, -1, 2));
        int titleRes = category < CATEGORIES.length ? CATEGORIES[category]
                : ModuleRegistry.all()[category - CATEGORIES.length].nameResId();
        TextView paneTitle = text(getString(titleRes), 26, WHITE, medium);
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
            showModules(content);
            return;
        } else if (category == 2) {
            TextView explanation = text(getString(R.string.export_changes_hint), 19, MUTED, sans);
            explanation.setPadding(dp(30), 0, dp(28), 0);
            content.addView(explanation, new LinearLayout.LayoutParams(-1, dp(85)));
            TextView export = action(getString(R.string.export_changes_usb), true);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(60));
            ep.leftMargin = dp(30);
            ep.rightMargin = dp(28);
            content.addView(export, ep);
            export.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    new Thread(new Runnable() {
                        public void run() {
                            final RootResult result = RootClient.exportUsb();
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setMessage(result.detail)
                                            .setPositiveButton("OK", null).show();
                                }
                            });
                        }
                    }, "export-changes").start();
                }
            });
            return;
        } else if (category == 3) {
            addRow(content, R.string.app, R.string.ui_mock, R.string.app_name, -1);
            addRow(content, R.string.version, R.string.preview_build, installedVersion(), -1);
            addRow(content, R.string.android, R.string.minimum_api, R.string.value_api, -1);
            addRow(content, R.string.platform, R.string.vehicle_brands, R.string.value_gen5w, -1);
            return;
        } else {
            showModulePage(content, ModuleRegistry.all()[category - CATEGORIES.length]);
            return;
        }
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

    private void showModules(final LinearLayout content) {
        final ScrollView scroll = new ScrollView(this);
        final LinearLayout rows = vertical();
        rows.setPadding(dp(30), dp(10), dp(28), dp(20));
        scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        rows.addView(text(getString(R.string.module_loading), 20, MUTED, sans),
                new LinearLayout.LayoutParams(-1, dp(75)));
        new Thread(new Runnable() {
            public void run() {
                final Module[] modules = ModuleRegistry.all();
                final RootResult[] results = new RootResult[modules.length];
                for (int i = 0; i < modules.length; i++) {
                    results[i] = RootClient.run(modules[i].id(), "status");
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!settingsVisible || category != 1) return;
                        rows.removeAllViews();
                        for (int i = 0; i < modules.length; i++) {
                            if (results[i] != null) addModule(rows, modules[i], results[i]);
                        }
                    }
                });
            }
        }, "module-status").start();
    }

    private void addModule(LinearLayout parent, final Module module, RootResult result) {
        final boolean enabled = "on".equals(result.state) || "pending-restart".equals(result.state);
        final boolean observed = enabled || "off".equals(result.state);
        LinearLayout row = horizontal();
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setBackgroundDrawable(selectable(RAISED, HAIRLINE, 8, false));
        LinearLayout copy = vertical();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(text(getString(module.nameResId()), 23, WHITE, medium));
        copy.addView(text(getString(module.descriptionResId()), 17, MUTED, sans));
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        TextView toggle = text(getString(observed ? (enabled ? R.string.on : R.string.off)
                : R.string.module_unavailable), 20, enabled ? BLUE : MUTED, mono);
        toggle.setGravity(Gravity.CENTER);
        toggle.setBackgroundDrawable(shape(enabled ? 0xFF17283A : SURFACE,
                enabled ? BLUE : HAIRLINE, 6));
        row.addView(toggle, new LinearLayout.LayoutParams(dp(96), dp(54)));
        if (observed) row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { prepareModuleChange(module, !enabled); }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(82));
        rp.bottomMargin = dp(8);
        parent.addView(row, rp);
        String stateText = getString(R.string.module_state) + ": " + result.state;
        if (result.detail.length() > 0) stateText += " · " + result.detail;
        parent.addView(text(stateText, 18, MUTED, sans),
                new LinearLayout.LayoutParams(-1, dp(34)));
        if ("pending-restart".equals(result.state)) {
            TextView banner = text(getString(R.string.module_restart), 20, WHITE, medium);
            banner.setPadding(dp(18), 0, dp(18), 0);
            banner.setBackgroundDrawable(shape(0xFF263347, BLUE, 6));
            parent.addView(banner, new LinearLayout.LayoutParams(-1, dp(60)));
        }
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(-1, dp(12)));
    }

    private void showModulePage(final LinearLayout content, final Module module) {
        final ScrollView scroll = new ScrollView(this);
        final LinearLayout rows = vertical();
        rows.setPadding(dp(30), dp(10), dp(28), dp(20));
        scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        rows.addView(text(getString(R.string.module_loading), 20, MUTED, sans),
                new LinearLayout.LayoutParams(-1, dp(75)));
        new Thread(new Runnable() {
            public void run() {
                final RootResult result = RootClient.run(module.id(), "status");
                final SettingsPage page = new SettingsPage();
                module.buildSettingsPage(page);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!settingsVisible || category < CATEGORIES.length ||
                                !ModuleRegistry.all()[category - CATEGORIES.length].id().equals(module.id()))
                            return;
                        rows.removeAllViews();
                        for (SettingsPage.Item item : page.items())
                            renderPageItem(rows, module, result, item);
                    }
                });
            }
        }, "module-settings").start();
    }

    private void renderPageItem(LinearLayout rows, final Module module,
            RootResult result, SettingsPage.Item item) {
        switch (item.type) {
            case STATUS_HEADER:
                addModule(rows, module, result);
                break;
            case SECTION:
                rows.addView(text(getString(item.labelResId), 22, WHITE, medium),
                        new LinearLayout.LayoutParams(-1, dp(55)));
                break;
            case INFO:
                String value = result.info.get(item.key);
                rows.addView(text(getString(item.labelResId) + ": " +
                        (value == null || value.length() == 0 ? "—" : value), 19, WHITE, sans),
                        new LinearLayout.LayoutParams(-1, dp(55)));
                break;
            case NOTE:
            case WARNING:
                TextView note = text(getString(item.labelResId), 18,
                        item.type == SettingsPage.Type.WARNING ? 0xFFFFB366 : MUTED, sans);
                rows.addView(note, new LinearLayout.LayoutParams(-1, dp(60)));
                break;
            case TOGGLE:
            case CHOICE:
                TextView setting = text(getString(item.labelResId), 21, WHITE, medium);
                rows.addView(setting, new LinearLayout.LayoutParams(-1, dp(75)));
                break;
            case ACTION:
                final ModuleAction action = item.action;
                TextView button = action(getString(item.labelResId), false);
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(60));
                ap.bottomMargin = dp(8);
                rows.addView(button, ap);
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        if (action.type == ModuleAction.Type.ENABLE_MODULE)
                            prepareModuleChange(ModuleRegistry.find(action.target), true);
                        else if (action.type == ModuleAction.Type.OPEN_STOCK_WIFI_SETTINGS)
                            startActivity(new Intent("android.settings.WIFI_SETTINGS"));
                    }
                });
                break;
        }
    }

    private void prepareModuleChange(final Module module, final boolean enable) {
        new Thread(new Runnable() {
            public void run() {
                final ModulePlan plan;
                try { plan = new ModuleManager(MainActivity.this).plan(module.id(), enable); }
                catch (final IllegalStateException error) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            new AlertDialog.Builder(MainActivity.this).setMessage(error.getMessage())
                                    .setPositiveButton("OK", null).show();
                        }
                    });
                    return;
                }
                final RootResult precheck = enable ? RootClient.run(module.id(), "precheck") : null;
                runOnUiThread(new Runnable() {
                    public void run() { confirmModuleChange(module, enable, plan, precheck); }
                });
            }
        }, "module-plan").start();
    }

    private void confirmModuleChange(final Module module, final boolean enable,
            final ModulePlan plan, RootResult precheck) {
        StringBuilder message = new StringBuilder();
        for (ModulePlan.Step step : plan.steps) {
            message.append(step.enable ? "+ " : "− ")
                    .append(getString(ModuleRegistry.find(step.id).nameResId())).append('\n');
        }
        if (plan.steps.isEmpty()) message.append(getString(R.string.module_no_changes));
        if (precheck != null) {
            for (Warning warning : module.enableWarnings()) {
                if ("1".equals(precheck.warnings.get(warning.checkKey)))
                    message.append('\n').append(getString(warning.messageResId));
            }
            if (precheck.exitCode != 0) message.append('\n').append(precheck.detail);
        }
        new AlertDialog.Builder(this).setTitle(getString(R.string.module_confirm))
                .setMessage(message.toString())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        new Thread(new Runnable() {
                            public void run() {
                                final RootResult outcome = enable
                                        ? new ModuleManager(MainActivity.this).requestEnable(module.id(), "settings")
                                        : new ModuleManager(MainActivity.this).execute(plan);
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        if (outcome.exitCode != 0)
                                            new AlertDialog.Builder(MainActivity.this)
                                                    .setMessage(outcome.detail).setPositiveButton("OK", null).show();
                                        showSettings();
                                    }
                                });
                            }
                        }, "module-operation").start();
                    }
                }).show();
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
        updateStatus = text(getString(getSharedPreferences("update", MODE_PRIVATE).contains("restore") ? R.string.update_restore_pending : R.string.update_idle), 18, MUTED, sans);
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
                                RootResult result = new ModuleManager(MainActivity.this).requestEnable("wifi-client", "update-check");
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
                } catch (UpdateClient.NoUpdateException e) {
                    boolean restored = restoreModules();
                    updateMessage(getString(R.string.update_no_update) + (restored ? "" : " · " + getString(R.string.update_restore_pending)));
                } catch (Exception e) {
                    boolean restored = restoreModules();
                    updateMessage(e.getMessage() + (restored ? "" : " · " + getString(R.string.update_restore_pending)));
                }
            }}, "seogaemo-update-check").start();
        } catch (Exception e) { updateMessage(e.getMessage()); }
    }

    private void confirmInstall(final String version) {
        new AlertDialog.Builder(this).setMessage(getString(R.string.update_available, version))
            .setPositiveButton(R.string.update_install, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { rootUpdate(true); }
            }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { new Thread(new Runnable() { public void run() { if (!restoreModules()) updateMessage(getString(R.string.update_restore_pending)); } }).start(); }
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
                updateMessage(getString(restoreModules() ? R.string.update_complete : R.string.update_restore_pending));
            } catch (Exception e) {
                boolean restored = restoreModules();
                updateMessage(e.getMessage() + (restored ? "" : " · " + getString(R.string.update_restore_pending)));
            }
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

    private boolean restoreModules() {
        SharedPreferences pending = getSharedPreferences("update", MODE_PRIVATE);
        if (!pending.contains("restore")) return true;
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
        for (Module module : ModuleRegistry.all()) {
            boolean wanted = ("," + desired).contains("," + module.id() + ",");
            String observed = RootClient.run(module.id(), "status").state;
            if (wanted ? !"on".equals(observed) : !("off".equals(observed) || "not-implemented".equals(observed))) success = false;
        }
        if (success) pending.edit().clear().apply();
        return success;
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

    private void addModuleCategory(LinearLayout parent, int moduleIndex, Module module) {
        final int index = CATEGORIES.length + moduleIndex;
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
        copy.addView(text(getString(module.nameResId()), 21,
                index == category ? WHITE : 0xFFD0D0D0, medium));
        copy.addView(text(getString(R.string.module_settings), 16, MUTED, sans));
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { category = index; showSettings(); }
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
