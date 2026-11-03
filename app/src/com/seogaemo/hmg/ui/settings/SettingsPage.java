package com.seogaemo.hmg.ui.settings;

import com.seogaemo.hmg.module.ModuleAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Declarative page content; modules never construct Android views. */
public final class SettingsPage {
    public enum Type { SECTION, INFO, TOGGLE, CHOICE, ACTION, NOTE, WARNING, STATUS_HEADER }

    public static final class Item {
        public final Type type;
        public final int labelResId;
        public final String key;
        public final int[] valuesRes;
        public final ModuleAction action;

        private Item(Type type, int labelResId, String key, int[] valuesRes,
                ModuleAction action) {
            this.type = type;
            this.labelResId = labelResId;
            this.key = key;
            this.valuesRes = valuesRes == null ? null : valuesRes.clone();
            this.action = action;
        }
    }

    private final List<Item> items = new ArrayList<Item>();
    public SettingsPage section(int titleRes) {
        items.add(new Item(Type.SECTION, titleRes, null, null, null)); return this;
    }
    public SettingsPage info(int labelRes, String infoKey) {
        items.add(new Item(Type.INFO, labelRes, infoKey, null, null)); return this;
    }
    public SettingsPage toggle(int labelRes, String settingKey) {
        items.add(new Item(Type.TOGGLE, labelRes, settingKey, null, null)); return this;
    }
    public SettingsPage choice(int labelRes, String settingKey, int[] valuesRes) {
        items.add(new Item(Type.CHOICE, labelRes, settingKey, valuesRes, null)); return this;
    }
    public SettingsPage action(int labelRes, ModuleAction action) {
        items.add(new Item(Type.ACTION, labelRes, null, null, action)); return this;
    }
    public SettingsPage note(int textRes) {
        items.add(new Item(Type.NOTE, textRes, null, null, null)); return this;
    }
    public SettingsPage warning(int textRes) {
        items.add(new Item(Type.WARNING, textRes, null, null, null)); return this;
    }
    public SettingsPage statusHeader() {
        items.add(new Item(Type.STATUS_HEADER, 0, null, null, null)); return this;
    }
    public List<Item> items() { return Collections.unmodifiableList(items); }
}
