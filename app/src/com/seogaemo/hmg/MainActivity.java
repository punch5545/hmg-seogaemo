package com.seogaemo.hmg;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.seogaemo.hmg.module.InfoRow;
import com.seogaemo.hmg.module.HomeTile;
import com.seogaemo.hmg.module.HomeTileCatalog;
import com.seogaemo.hmg.module.Module;
import com.seogaemo.hmg.module.ModuleAction;
import com.seogaemo.hmg.module.ModuleManager;
import com.seogaemo.hmg.module.ModulePlan;
import com.seogaemo.hmg.module.ModuleRegistry;
import com.seogaemo.hmg.module.RootClient;
import com.seogaemo.hmg.module.RootResult;
import com.seogaemo.hmg.module.Warning;
import com.seogaemo.hmg.ui.settings.SettingsPage;
import com.seogaemo.hmg.ui.settings.SettingsUi;
import com.seogaemo.hmg.ui.ConfirmDialog;
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
    private static final int[] CATEGORIES = {R.string.cat_general, R.string.cat_mapping, R.string.cat_modules,
        R.string.cat_logs, R.string.cat_about};
    private static final int[] CATEGORY_SUBS = {R.string.cat_general_sub, R.string.cat_mapping_sub, R.string.cat_modules_sub,
        R.string.cat_logs_sub, R.string.cat_about_sub};
    private Typeface sans;
    private Typeface medium;
    private int category = 0;
    private int sidebarOffset;
    private ScrollView sidebarScroll;
    private boolean settingsVisible;
    private HomeView homeView;
    private TextView updateStatus;
    private boolean updateBusy;
    private boolean modulePlanning;
    private boolean moduleWorking;
    private RootResult mapStatus;
    private boolean mapRefreshing;
    private boolean mapBusy;
    private String mapResult;
    private TextView stockStatus;
    private boolean stockBusy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        applyLocale();
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        sans = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        if (state != null && state.getBoolean("settings", false)) {
            category = state.getInt("category", 0);
            sidebarOffset = state.getInt("sidebarOffset", 0);
            showSettings();
        } else showHome();
        refreshMap(true);
        if (getSharedPreferences("update", MODE_PRIVATE).getBoolean("resume", false)) {
            try {
                if (new UpdateClient(this).hasUsableNetwork()) {
                    category = 0;
                    showSettings();
                    checkUpdate(); // The earlier confirmation saved this resume intent.
                }
            } catch (Exception ignored) { }
        } else if (getSharedPreferences("update", MODE_PRIVATE).contains("restore")) {
            updateBusy = true;
            new Thread(new Runnable() { public void run() {
                if (!restoreModules()) updateMessage(getString(R.string.update_restore_pending));
                finishUpdateWork(null);
            } }, "hmg-seogaemo-update-restore").start();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("settings", settingsVisible);
        state.putInt("category", category);
        state.putInt("sidebarOffset", sidebarScroll == null ? sidebarOffset : sidebarScroll.getScrollY());
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!settingsVisible && homeView != null) refreshHomeTiles(homeView);
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
        bar.addView(new BrandMark(), new LinearLayout.LayoutParams(dp(32), dp(32)));
        View gap = new View(this);
        bar.addView(gap, new LinearLayout.LayoutParams(dp(12), dp(60)));
        TextView brand = text(getString(R.string.app_name), 28, WHITE, medium);
        bar.addView(brand, new LinearLayout.LayoutParams(dp(256), dp(60)));
        if (back) {
            TextView section = text(getString(R.string.settings), 22, MUTED, sans);
            section.setPadding(dp(SettingsUi.MD), 0, 0, 0);
            bar.addView(section, new LinearLayout.LayoutParams(dp(130), dp(60)));
        }
        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, dp(60), 1));
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
        homeView = new HomeView(this, new Runnable() {
            public void run() { showSettings(); }
        }, new HomeView.ActionHandler() {
            public void run(ModuleAction action) { dispatchModuleAction(action); }
        });
        r.addView(homeView, new LinearLayout.LayoutParams(-1, 0, 1));
        refreshHomeTiles(homeView);
    }

    private void refreshHomeTiles(final HomeView view) {
        new Thread(new Runnable() {
            public void run() {
                Module[] modules = ModuleRegistry.all();
                HomeTileCatalog.Entry[] entries = new HomeTileCatalog.Entry[modules.length];
                for (int i = 0; i < modules.length; i++) {
                    RootResult status = RootClient.run(modules[i].id(), "status");
                    boolean enabled = status.exitCode == 0 && moduleEnabled(status);
                    entries[i] = new HomeTileCatalog.Entry(modules[i].id(), enabled, modules[i].homeTiles());
                }
                final List<HomeTile> tiles = HomeTileCatalog.collect(HomeView.settingsTile(), entries);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (homeView == view && !settingsVisible) view.setTiles(tiles);
                    }
                });
            }
        }, "home-tiles").start();
    }

    private void dispatchModuleAction(ModuleAction action) {
        if (action.type == ModuleAction.Type.ENABLE_MODULE)
            prepareModuleChange(ModuleRegistry.find(action.target), true, false);
        else if (action.type == ModuleAction.Type.OPEN_STOCK_WIFI_SETTINGS)
            startActivity(new Intent("android.settings.WIFI_SETTINGS"));
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
        addSidebarSection(side, R.string.app, 0);
        for (int i = 0; i < CATEGORIES.length; i++) addCategory(side, i);
        View sectionDivider = new View(this);
        sectionDivider.setBackgroundColor(0xFF555555);
        side.addView(sectionDivider, new LinearLayout.LayoutParams(-1, dp(1)));
        addSidebarSection(side, R.string.module_settings, 24);
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
        paneTitle.setPadding(dp(SettingsUi.PAGE), 0, 0, 0);
        content.addView(paneTitle, new LinearLayout.LayoutParams(-1, dp(52)));
        divider(content, false);
        if (category == 0) {
            ScrollView scroll = new ScrollView(this);
            LinearLayout general = vertical();
            scroll.addView(general);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            showLanguage(general);
            showUpdate(general);
            showStock(general);
            return;
        } else if (category == 1) {
            showMapping(content);
            return;
        } else if (category == 2) {
            showModules(content);
            return;
        } else if (category == 3) {
            TextView explanation = text(getString(R.string.export_changes_hint), 19, MUTED, sans);
            explanation.setPadding(dp(SettingsUi.PAGE), 0, dp(SettingsUi.PAGE), 0);
            content.addView(explanation, new LinearLayout.LayoutParams(-1, dp(85)));
            TextView export = action(getString(R.string.export_changes_usb), true);
            LinearLayout.LayoutParams ep = SettingsUi.contentAction(this);
            content.addView(export, ep);
            export.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ConfirmDialog.show(MainActivity.this, getString(R.string.export_changes_usb),
                            getString(R.string.export_changes_hint), getString(R.string.export_changes_usb),
                            getString(R.string.cancel), false, new ConfirmDialog.Action() {
                        public void run(final ConfirmDialog dialog) {
                            dialog.showProgress();
                            new Thread(new Runnable() {
                                public void run() {
                                    final RootResult result = RootClient.exportUsb();
                                    runOnUiThread(new Runnable() {
                                        public void run() {
                                            dialog.dismiss();
                                            new AlertDialog.Builder(MainActivity.this)
                                                    .setMessage(result.detail)
                                                    .setPositiveButton("OK", null).show();
                                        }
                                    });
                                }
                            }, "export-changes").start();
                        }
                    }, null);
                }
            });
            return;
        } else if (category == 4) {
            addRow(content, R.string.app, R.string.app_description, R.string.app_name);
            addRow(content, R.string.version, R.string.installed_version, installedVersion());
            addRow(content, R.string.android, R.string.minimum_api, R.string.value_api);
            addRow(content, R.string.platform, R.string.vehicle_brands, R.string.value_gen5w);
            return;
        } else {
            showModulePage(content, ModuleRegistry.all()[category - CATEGORIES.length]);
            return;
        }
    }

    private void showLanguage(LinearLayout content) {
        TextView label = text(getString(R.string.language), 24, WHITE, medium);
        label.setPadding(dp(SettingsUi.PAGE), 0, 0, 0);
        content.addView(label, new LinearLayout.LayoutParams(-1, dp(48)));
        addLanguageOption(content, R.string.language_system, "system");
        addLanguageOption(content, R.string.language_english, "en");
        addLanguageOption(content, R.string.language_korean, "ko");
        TextView hint = text(getString(R.string.language_scope), 18, MUTED, sans);
        hint.setPadding(dp(SettingsUi.PAGE), 0, 0, 0);
        content.addView(hint, new LinearLayout.LayoutParams(-1, dp(40)));
    }

    private String mapInfo(String key) {
        String value = mapStatus == null ? null : mapStatus.info.get(key);
        return value == null || value.length() == 0 ? "—" : value;
    }

    private void showMapping(LinearLayout content) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = vertical();
        rows.setPadding(0, dp(SettingsUi.SM), 0, dp(SettingsUi.LG));
        scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        int status = R.string.mapping_loading;
        if (mapStatus != null) {
            if ("on".equals(mapStatus.state)) status = R.string.mapping_ready;
            else if ("off".equals(mapStatus.state)) status = R.string.mapping_verification_needed;
            else if ("incompatible".equals(mapStatus.state)) status = R.string.mapping_needed;
            else status = R.string.mapping_unavailable;
        }
        addRow(rows, R.string.mapping_state, R.string.mapping_state_hint,
                getString(mapBusy ? R.string.mapping_progress : status));
        addRow(rows, R.string.mapping_product, R.string.mapping_product_hint, mapInfo("product"));
        String source = mapInfo("source");
        if ("bundled".equals(source)) source = getString(R.string.mapping_source_bundled);
        else if ("generated".equals(source)) source = getString(R.string.mapping_source_generated);
        else if ("none".equals(source)) source = getString(R.string.mapping_source_none);
        addRow(rows, R.string.mapping_source, R.string.mapping_source_hint, source);
        addRow(rows, R.string.mapping_points, R.string.mapping_points_hint,
                mapInfo("mapped") + " / " + mapInfo("verified") + " / " + mapInfo("failed"));
        TextView failures = text(getString(R.string.mapping_failing_ids) + ": " + mapInfo("failing_ids"), 18, MUTED, sans);
        failures.setPadding(dp(SettingsUi.PAGE), dp(SettingsUi.SM), dp(SettingsUi.PAGE), dp(SettingsUi.SM));
        rows.addView(failures, new LinearLayout.LayoutParams(-1, -2));
        if (mapResult != null) {
            TextView result = text(mapResult, 18, WHITE, sans);
            result.setPadding(dp(SettingsUi.PAGE), dp(SettingsUi.SM), dp(SettingsUi.PAGE), dp(SettingsUi.SM));
            rows.addView(result, new LinearLayout.LayoutParams(-1, -2));
        }
        TextView map = action(getString(R.string.mapping_run), true);
        LinearLayout.LayoutParams mp = SettingsUi.contentAction(this);
        mp.topMargin = dp(SettingsUi.SM);
        rows.addView(map, mp);
        map.setEnabled(!mapBusy);
        map.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmMapRun(); } });
        TextView export = action(getString(R.string.mapping_export), false);
        LinearLayout.LayoutParams ep = SettingsUi.contentAction(this);
        ep.topMargin = dp(SettingsUi.SM);
        rows.addView(export, ep);
        export.setEnabled(!mapBusy);
        export.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmMapExport(); } });
        TextView note = text(getString(R.string.mapping_pr_hint), 18, MUTED, sans);
        note.setPadding(dp(SettingsUi.PAGE), dp(SettingsUi.MD), dp(SettingsUi.PAGE), dp(SettingsUi.SM));
        rows.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refreshMap(final boolean verifyKnown) {
        if (mapRefreshing) return;
        mapRefreshing = true;
        new Thread(new Runnable() { public void run() {
            RootResult status = RootClient.map("status");
            String failure = null;
            if (verifyKnown && status.exitCode == 0 && "off".equals(status.state) &&
                    !"none".equals(status.info.get("source"))) {
                RootResult verification = RootClient.map("verify");
                if (verification.exitCode != 0) failure = verification.detail;
                status = RootClient.map("status");
            }
            final RootResult observed = status;
            final String observedFailure = failure;
            runOnUiThread(new Runnable() { public void run() {
                mapStatus = observed;
                if (observedFailure != null) mapResult = observedFailure;
                mapRefreshing = false;
                if (settingsVisible && category == 1) showSettings();
            } });
        } }, "car-map-status").start();
    }

    private void confirmMapRun() {
        if (mapBusy || stockBusy) return;
        ConfirmDialog.show(this, getString(R.string.mapping_run), getString(R.string.mapping_run_confirm),
                getString(R.string.mapping_run), getString(R.string.cancel), false,
                new ConfirmDialog.Action() { public void run(ConfirmDialog dialog) { mapOperation("run", dialog); } }, null);
    }

    private void confirmMapExport() {
        if (mapBusy || stockBusy) return;
        ConfirmDialog.show(this, getString(R.string.mapping_export), getString(R.string.mapping_export_confirm),
                getString(R.string.mapping_export), getString(R.string.cancel), false,
                new ConfirmDialog.Action() { public void run(ConfirmDialog dialog) { mapOperation("export", dialog); } }, null);
    }

    private void mapOperation(final String verb, final ConfirmDialog dialog) {
        mapBusy = true;
        dialog.showProgress();
        if (settingsVisible && category == 1) showSettings();
        new Thread(new Runnable() { public void run() {
            final RootResult outcome = RootClient.map(verb);
            final RootResult observed = RootClient.map("status");
            runOnUiThread(new Runnable() { public void run() {
                mapBusy = false;
                mapStatus = observed;
                mapResult = outcome.exitCode == 0 && "run".equals(verb)
                        ? getString(R.string.mapping_run_done) : outcome.detail;
                dialog.dismiss();
                if (settingsVisible && category == 1) showSettings();
                new AlertDialog.Builder(MainActivity.this).setMessage(mapResult)
                        .setPositiveButton("OK", null).show();
            } });
        } }, "car-map-" + verb).start();
    }

    private void showModules(final LinearLayout content) {
        final ScrollView scroll = new ScrollView(this);
        final LinearLayout rows = vertical();
        rows.setPadding(dp(SettingsUi.PAGE), dp(SettingsUi.SM),
                dp(SettingsUi.PAGE), dp(SettingsUi.LG));
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
                        if (!settingsVisible || category != 2) return;
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
        final boolean enabled = moduleEnabled(result);
        final boolean observed = enabled || "off".equals(result.state) ||
                ("pending-restart".equals(result.state) && "off".equals(result.info.get("desired")));
        LinearLayout row = horizontal();
        SettingsUi.cardRow(this, row);
        row.setBackgroundDrawable(selectable(RAISED, HAIRLINE, 8, false));
        LinearLayout copy = vertical();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        SettingsUi.titleBlock(this, copy, text(getString(module.nameResId()), 23, WHITE, medium),
                text(getString(module.descriptionResId()), 17, MUTED, sans));
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        TextView toggle = text(getString(observed ? (enabled ? R.string.on : R.string.off)
                : R.string.module_unavailable), 20, enabled ? BLUE : MUTED, medium);
        SettingsUi.pill(this, toggle, shape(enabled ? 0xFF17283A : SURFACE,
                enabled ? BLUE : HAIRLINE, 6));
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                dp(SettingsUi.STATUS_WIDTH), dp(SettingsUi.STATUS_HEIGHT));
        toggleParams.leftMargin = dp(SettingsUi.MD);
        row.addView(toggle, toggleParams);
        if (observed) row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { prepareModuleChange(module, !enabled, false); }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(82));
        rp.bottomMargin = dp(SettingsUi.XS);
        parent.addView(row, rp);
        String stateText = moduleStateText(result);
        parent.addView(text(stateText, 18, MUTED, sans),
                new LinearLayout.LayoutParams(-1, dp(40)));
        if ("pending-restart".equals(result.state)) {
            TextView banner = text(getString(!enabled
                    ? R.string.module_pending_disable : R.string.module_pending_enable), 20, WHITE, medium);
            banner.setPadding(dp(SettingsUi.MD), 0, dp(SettingsUi.MD), 0);
            banner.setBackgroundDrawable(shape(0xFF263347, BLUE, 6));
            parent.addView(banner, new LinearLayout.LayoutParams(-1, dp(60)));
        }
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(-1, dp(SettingsUi.SM)));
    }

    private String moduleStateText(RootResult result) {
        if ("on".equals(result.state)) return getString(R.string.module_state) + ": " + getString(R.string.on);
        if ("off".equals(result.state)) return getString(R.string.module_state) + ": " + getString(R.string.off);
        if ("pending-restart".equals(result.state)) return getString("off".equals(result.info.get("desired"))
                ? R.string.module_pending_disable : R.string.module_pending_enable);
        if ("conflict".equals(result.state)) return getString(R.string.module_status_conflict);
        if ("incompatible".equals(result.state)) return getString(R.string.module_status_incompatible);
        if ("not-implemented".equals(result.state)) return getString(R.string.module_status_unsupported);
        if (result.detail.length() > 0) Log.w("HMG-Seogaemo", "Module status: " + result.detail);
        return getString(R.string.module_status_unknown);
    }

    private void showModulePage(final LinearLayout content, final Module module) {
        final ScrollView scroll = new ScrollView(this);
        final LinearLayout rows = vertical();
        rows.setPadding(dp(SettingsUi.PAGE), dp(SettingsUi.SM),
                dp(SettingsUi.PAGE), dp(SettingsUi.LG));
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
                note.setMinHeight(dp(60));
                note.setPadding(0, dp(SettingsUi.XS), 0, dp(SettingsUi.XS));
                rows.addView(note, new LinearLayout.LayoutParams(-1, -2));
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
                ap.bottomMargin = dp(SettingsUi.XS);
                rows.addView(button, ap);
                button.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        dispatchModuleAction(action);
                    }
                });
                break;
        }
    }

    private void prepareModuleChange(final Module module, final boolean enable,
            final boolean forUpdate) {
        if (modulePlanning || moduleWorking || stockBusy) return;
        modulePlanning = true;
        new Thread(new Runnable() {
            public void run() {
                final ModulePlan plan;
                try { plan = new ModuleManager(MainActivity.this).plan(module.id(), enable); }
                catch (final IllegalStateException error) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            modulePlanning = false;
                            new AlertDialog.Builder(MainActivity.this).setMessage(error.getMessage())
                                    .setPositiveButton("OK", null).show();
                        }
                    });
                    return;
                }
                final RootResult precheck = enable ? RootClient.run(module.id(), "precheck") : null;
                runOnUiThread(new Runnable() {
                    public void run() {
                        modulePlanning = false;
                        confirmModuleChange(module, enable, plan, precheck, forUpdate);
                    }
                });
            }
        }, "module-plan").start();
    }

    private void confirmModuleChange(final Module module, final boolean enable,
            final ModulePlan plan, RootResult precheck, final boolean forUpdate) {
        StringBuilder message = new StringBuilder();
        if (forUpdate) message.append(getString(R.string.update_wifi_required)).append("\n\n");
        for (ModulePlan.Step step : plan.steps) {
            message.append("• ").append(getString(step.enable ? R.string.plan_step_enable : R.string.plan_step_disable,
                    getString(ModuleRegistry.find(step.id).nameResId()))).append('\n');
        }
        if (!enable && module.rebootRestore())
            message.append('\n').append(getString(R.string.module_restore_reboot));
        if (plan.steps.isEmpty()) message.append(getString(R.string.module_no_changes));
        if (precheck != null) {
            for (Warning warning : module.enableWarnings()) {
                if ("1".equals(precheck.warnings.get(warning.checkKey)))
                    message.append('\n').append("• ").append(getString(warning.messageResId));
            }
            if (precheck.exitCode != 0) message.append('\n').append(precheck.detail);
        }
        ConfirmDialog.show(this, getString(R.string.module_confirm), message.toString(),
                getString(forUpdate ? R.string.update_enable_wifi : R.string.apply),
                getString(R.string.cancel), !enable, new ConfirmDialog.Action() {
                    public void run(final ConfirmDialog dialog) {
                        moduleWorking = true;
                        dialog.showProgress();
                        new Thread(new Runnable() {
                            public void run() {
                                if (forUpdate) {
                                    String prior = enabledModules();
                                    getSharedPreferences("update", MODE_PRIVATE).edit()
                                            .putString("restore", prior).putBoolean("resume", true).apply();
                                }
                                final RootResult outcome = enable
                                        ? new ModuleManager(MainActivity.this).requestEnable(module.id(),
                                                forUpdate ? "update-check" : "settings")
                                        : new ModuleManager(MainActivity.this).execute(plan);
                                if (forUpdate && outcome.exitCode != 0)
                                    getSharedPreferences("update", MODE_PRIVATE).edit().clear().apply();
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        moduleWorking = false;
                                        dialog.dismiss();
                                        if (outcome.exitCode != 0)
                                            new AlertDialog.Builder(MainActivity.this)
                                                    .setMessage(outcome.detail).setPositiveButton("OK", null).show();
                                        if (forUpdate) updateMessage(outcome.exitCode == 0
                                                ? getString(R.string.update_resume_after_restart) : outcome.detail);
                                        showSettings();
                                    }
                                });
                            }
                        }, "module-operation").start();
                    }
                }, null);
    }

    private void addLanguageOption(LinearLayout content, final int labelRes, final String choice) {
        boolean selected = choice.equals(preferences().getString("language", "system"));
        LinearLayout row = horizontal();
        SettingsUi.contentRow(this, row);
        row.setBackgroundDrawable(selectable(selected ? 0xFF1C2938 : CANVAS,
                Color.TRANSPARENT, 0, false));
        TextView title = text(getString(labelRes), 24, WHITE, medium);
        row.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        TextView radio = text(getString(selected ? R.string.radio_selected : R.string.radio_unselected), 28, selected ? BLUE : MUTED, sans);
        radio.setGravity(Gravity.CENTER);
        row.addView(radio, new LinearLayout.LayoutParams(dp(60), -1));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (choice.equals(preferences().getString("language", "system"))) return;
                ConfirmDialog.show(MainActivity.this, getString(R.string.language_confirm_title),
                        getString(R.string.language_confirm_message, getString(labelRes)),
                        getString(R.string.apply), getString(R.string.cancel), false,
                        new ConfirmDialog.Action() {
                    public void run(ConfirmDialog dialog) {
                        preferences().edit().putString("language", choice).apply();
                        dialog.dismiss();
                        recreate();
                    }
                }, null);
            }
        });
        content.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
        divider(content, false);
    }

    private void showUpdate(LinearLayout content) {
        addRow(content, R.string.update_current, R.string.update_current_hint, installedVersion());
        updateStatus = text(getString(getSharedPreferences("update", MODE_PRIVATE).contains("restore") ? R.string.update_restore_pending : R.string.update_idle), 18, MUTED, sans);
        updateStatus.setPadding(dp(SettingsUi.PAGE), 0, dp(SettingsUi.PAGE), 0);
        content.addView(updateStatus, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView check = action(getString(R.string.update_check), true);
        check.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmCheckUpdate(); } });
        LinearLayout.LayoutParams cp = SettingsUi.contentAction(this);
        content.addView(check, cp);
        TextView rollback = action(getString(R.string.update_rollback), false);
        rollback.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { confirmRollback(); } });
        LinearLayout.LayoutParams rp = SettingsUi.contentAction(this);
        rp.topMargin = dp(SettingsUi.SM);
        content.addView(rollback, rp);
    }

    private void showStock(LinearLayout content) {
        TextView restore = action(getString(R.string.stock_restore), false);
        restore.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { confirmStock(false); }
        });
        LinearLayout.LayoutParams restoreParams = SettingsUi.contentAction(this);
        restoreParams.topMargin = dp(SettingsUi.LG);
        content.addView(restore, restoreParams);
        TextView uninstall = action(getString(R.string.stock_uninstall), false);
        uninstall.setTextColor(ERROR);
        uninstall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { confirmStock(true); }
        });
        LinearLayout.LayoutParams uninstallParams = SettingsUi.contentAction(this);
        uninstallParams.topMargin = dp(SettingsUi.SM);
        content.addView(uninstall, uninstallParams);
        stockStatus = text(getString(R.string.stock_hint), 20, MUTED, sans);
        stockStatus.setPadding(dp(SettingsUi.PAGE), dp(SettingsUi.MD),
                dp(SettingsUi.PAGE), dp(SettingsUi.LG));
        content.addView(stockStatus, new LinearLayout.LayoutParams(-1, -2));
        final TextView target = stockStatus;
        new Thread(new Runnable() { public void run() {
            final RootResult result = RootClient.stock("status");
            runOnUiThread(new Runnable() { public void run() {
                if (!stockBusy && stockStatus == target && !result.info.isEmpty())
                    target.setText(stockResultText(result));
            }});
        }}, "hmg-seogaemo-stock-status").start();
    }

    private void confirmStock(final boolean uninstall) {
        if (stockBusy || updateBusy || moduleWorking || modulePlanning || mapBusy) return;
        final int title = uninstall ? R.string.stock_uninstall : R.string.stock_restore;
        ConfirmDialog.show(this, getString(title),
                getString(uninstall ? R.string.stock_uninstall_confirm : R.string.stock_restore_confirm),
                getString(title), getString(R.string.cancel), uninstall, new ConfirmDialog.Action() {
            public void run(final ConfirmDialog dialog) {
                stockBusy = true;
                // The confirmed stock action supersedes any saved OTA resume.
                // Commit before the root call in case removing the APK closes us.
                if (!getSharedPreferences("update", MODE_PRIVATE).edit().clear().commit() ||
                        !getSharedPreferences("module_resume", MODE_PRIVATE).edit().clear().commit()) {
                    stockBusy = false;
                    dialog.dismiss();
                    stockStatus.setText(getString(R.string.stock_failed));
                    return;
                }
                dialog.showProgress();
                stockStatus.setText(getString(R.string.stock_working));
                new Thread(new Runnable() { public void run() {
                    final RootResult result = uninstall ? RootClient.uninstallPackage() : RootClient.stock("restore");
                    runOnUiThread(new Runnable() { public void run() {
                        stockBusy = false;
                        dialog.dismiss();
                        stockStatus.setText(stockResultText(result));
                    }});
                }}, "hmg-seogaemo-stock-operation").start();
            }
        }, null);
    }

    private String stockResultText(RootResult result) {
        boolean queued = "queued".equals(result.info.get("uninstall"));
        StringBuilder message = new StringBuilder(getString(result.exitCode != 0 ||
                "failed".equals(result.info.get("uninstall"))
                ? R.string.stock_failed : queued ? R.string.stock_queued : R.string.stock_complete));
        for (Module module : ModuleRegistry.all()) {
            String value = result.info.get("module_" + module.id().replace('-', '_'));
            if (value == null) continue;
            int label = "reverted".equals(value) ? R.string.stock_reverted
                    : "conflict".equals(value) ? R.string.stock_conflict
                    : "failed".equals(value) ? R.string.stock_module_failed : R.string.stock_not_attempted;
            message.append("\n• ").append(getString(module.nameResId())).append(": ").append(getString(label));
        }
        if ("head_unit".equals(result.restart))
            message.append("\n\n").append(getString(R.string.stock_restart));
        if ("failed".equals(result.info.get("uninstall")))
            message.append("\n\n").append(getString(R.string.stock_uninstall_failed));
        return message.toString();
    }

    private void updateMessage(final String value) {
        runOnUiThread(new Runnable() { public void run() { if (updateStatus != null) updateStatus.setText(value); } });
    }

    private void confirmCheckUpdate() {
        if (updateBusy || stockBusy) return;
        ConfirmDialog.show(this, getString(R.string.update_check),
                getString(R.string.update_check_confirm), getString(R.string.update_check),
                getString(R.string.cancel), false, new ConfirmDialog.Action() {
            public void run(ConfirmDialog dialog) { checkUpdate(dialog); }
        }, null);
    }

    private void checkUpdate() { checkUpdate(null); }

    private void checkUpdate(final ConfirmDialog dialog) {
        if (updateBusy || stockBusy) { if (dialog != null) dialog.dismiss(); return; }
        try {
            final UpdateClient client = new UpdateClient(this);
            if (!client.hasUsableNetwork()) {
                if (dialog != null) dialog.dismiss();
                prepareModuleChange(ModuleRegistry.find("wifi-client"), true, true);
                return;
            }
            updateBusy = true;
            if (dialog != null) dialog.showProgress();
            new Thread(new Runnable() { public void run() {
                try {
                    final String version = client.download(installedVersion(), new UpdateClient.Progress() {
                        public void changed(int phase) { updateMessage(getString(phase)); }
                    });
                    getSharedPreferences("update", MODE_PRIVATE).edit().putBoolean("resume", false).apply();
                    runOnUiThread(new Runnable() { public void run() {
                        updateBusy = false;
                        if (dialog != null) dialog.dismiss();
                        confirmInstall(version);
                    } });
                } catch (UpdateClient.NoUpdateException e) {
                    boolean restored = restoreModules();
                    updateMessage(getString(R.string.update_no_update) + (restored ? "" : " · " + getString(R.string.update_restore_pending)));
                    finishUpdateWork(dialog);
                } catch (Exception e) {
                    boolean restored = restoreModules();
                    updateMessage(e.getMessage() + (restored ? "" : " · " + getString(R.string.update_restore_pending)));
                    finishUpdateWork(dialog);
                }
            }}, "hmg-seogaemo-update-check").start();
        } catch (Exception e) { if (dialog != null) dialog.dismiss(); updateMessage(e.getMessage()); }
    }

    private void finishUpdateWork(final ConfirmDialog dialog) {
        runOnUiThread(new Runnable() { public void run() {
            updateBusy = false;
            if (dialog != null) dialog.dismiss();
        } });
    }

    private void confirmInstall(final String version) {
        ConfirmDialog.show(this, getString(R.string.update_install),
                getString(R.string.update_available, version), getString(R.string.update_install),
                getString(R.string.cancel), false, new ConfirmDialog.Action() {
            public void run(ConfirmDialog dialog) { rootUpdate(true, dialog); }
        }, new Runnable() {
            public void run() {
                updateBusy = true;
                new Thread(new Runnable() {
                    public void run() {
                        if (!restoreModules()) updateMessage(getString(R.string.update_restore_pending));
                        finishUpdateWork(null);
                    }
                }, "hmg-seogaemo-update-restore").start();
            }
        });
    }

    private void confirmRollback() {
        if (updateBusy || stockBusy) return;
        ConfirmDialog.show(this, getString(R.string.update_rollback),
                getString(R.string.update_rollback_confirm), getString(R.string.update_rollback),
                getString(R.string.cancel), true, new ConfirmDialog.Action() {
            public void run(ConfirmDialog dialog) { rootUpdate(false, dialog); }
        }, null);
    }

    private void rootUpdate(final boolean install, final ConfirmDialog dialog) {
        updateBusy = true;
        dialog.showProgress();
        new Thread(new Runnable() { public void run() {
            try {
                if (install) { updateMessage(getString(R.string.update_staging)); UpdateClient.root("stage"); }
                updateMessage(getString(install ? R.string.update_applying : R.string.update_rolling_back));
                UpdateClient.root(install ? "apply" : "rollback");
                updateMessage(getString(restoreModules() ? R.string.update_complete : R.string.update_restore_pending));
            } catch (Exception e) {
                boolean restored = restoreModules();
                updateMessage(e.getMessage() + (restored ? "" : " · " + getString(R.string.update_restore_pending)));
            } finally { finishUpdateWork(dialog); }
        }}, "hmg-seogaemo-update-root").start();
    }

    private static boolean moduleEnabled(RootResult result) {
        return "on".equals(result.state) ||
                ("pending-restart".equals(result.state) && !"off".equals(result.info.get("desired")));
    }

    private String enabledModules() {
        StringBuilder enabled = new StringBuilder();
        for (Module module : ModuleRegistry.all()) {
            RootResult result = RootClient.run(module.id(), "status");
            if (moduleEnabled(result)) enabled.append(module.id()).append(',');
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
            RootResult observed = RootClient.run(module.id(), "status");
            boolean active = moduleEnabled(observed);
            if (active && !wanted && manager.execute(manager.plan(module.id(), false)).exitCode != 0) success = false;
        }
        for (Module module : ModuleRegistry.all()) {
            boolean wanted = ("," + desired).contains("," + module.id() + ",");
            RootResult observed = RootClient.run(module.id(), "status");
            boolean active = moduleEnabled(observed);
            if (wanted && !active && manager.execute(manager.plan(module.id(), true)).exitCode != 0) success = false;
        }
        for (Module module : ModuleRegistry.all()) {
            boolean wanted = ("," + desired).contains("," + module.id() + ",");
            RootResult observed = RootClient.run(module.id(), "status");
            if (wanted ? !moduleEnabled(observed) : !("off".equals(observed.state) ||
                    ("pending-restart".equals(observed.state) && !moduleEnabled(observed)) ||
                    "not-implemented".equals(observed.state))) success = false;
        }
        if (success) pending.edit().clear().apply();
        return success;
    }

    private void addSidebarSection(LinearLayout parent, int titleRes, int topSpacingDp) {
        TextView header = text(getString(titleRes), 17, MUTED, sans);
        header.setPadding(dp(SettingsUi.LG), dp(SettingsUi.XS), 0, 0);
        header.setFocusable(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(44));
        params.topMargin = dp(topSpacingDp);
        parent.addView(header, params);
    }

    private void addCategory(LinearLayout parent, final int index) {
        LinearLayout row = horizontal();
        row.setPadding(dp(SettingsUi.LG), 0, dp(SettingsUi.MD), 0);
        row.setBackgroundDrawable(selectable(index == category ? 0xFF1C2938 : SURFACE,
                Color.TRANSPARENT, 0, false));
        View marker = new View(this);
        marker.setBackgroundColor(index == category ? BLUE : Color.TRANSPARENT);
        row.addView(marker, new LinearLayout.LayoutParams(dp(4), dp(56)));
        LinearLayout copy = vertical();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(SettingsUi.MD), 0, 0, 0);
        TextView name = text(getString(CATEGORIES[index]), 23, index == category ? WHITE : 0xFFD0D0D0, medium);
        TextView sub = text(getString(CATEGORY_SUBS[index]), 17, MUTED, sans);
        SettingsUi.titleBlock(this, copy, name, sub);
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { category = index; showSettings(); if (index == 1) refreshMap(false); }
        });
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(70)));
        divider(parent, false);
    }

    private void addModuleCategory(LinearLayout parent, int moduleIndex, Module module) {
        final int index = CATEGORIES.length + moduleIndex;
        LinearLayout row = horizontal();
        row.setPadding(dp(SettingsUi.LG), 0, dp(SettingsUi.MD), 0);
        row.setBackgroundDrawable(selectable(index == category ? 0xFF1C2938 : SURFACE,
                Color.TRANSPARENT, 0, false));
        View marker = new View(this);
        marker.setBackgroundColor(index == category ? BLUE : Color.TRANSPARENT);
        row.addView(marker, new LinearLayout.LayoutParams(dp(4), dp(56)));
        LinearLayout copy = vertical();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(SettingsUi.MD), 0, 0, 0);
        copy.addView(text(getString(module.nameResId()), 21,
                index == category ? WHITE : 0xFFD0D0D0, medium));
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

    private void addRow(LinearLayout parent, int label, int sub, int value) {
        addRow(parent, label, sub, value == 0 ? "" : getString(value));
    }

    private void addRow(LinearLayout parent, int label, int sub, String value) {
        LinearLayout row = horizontal();
        SettingsUi.contentRow(this, row);
        row.setBackgroundDrawable(selectable(CANVAS, Color.TRANSPARENT, 0, false));
        LinearLayout copy = vertical();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(getString(label), 24, WHITE, medium);
        TextView detail = text(getString(sub), 18, MUTED, sans);
        SettingsUi.titleBlock(this, copy, title, detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        TextView trailing = text(value, 20, WHITE, sans);
        trailing.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(240), -1);
        valueParams.leftMargin = dp(SettingsUi.MD);
        row.addView(trailing, valueParams);
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(75)));
        divider(parent, false);
    }

    private TextView action(String label, boolean primary) {
        TextView b = text(label, 21, WHITE, medium);
        SettingsUi.button(this, b, selectable(primary ? BLUE : RAISED,
                primary ? BLUE : HAIRLINE, 6, false));
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
