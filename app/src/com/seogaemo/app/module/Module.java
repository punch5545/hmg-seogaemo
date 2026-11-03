package com.seogaemo.app.module;

/** Compile-time contract for built-in modules. No operation is wired to the UI yet. */
public interface Module {
    enum RestartRequirement { NONE, ADBD, HEAD_UNIT }
    enum SettingType { BOOLEAN, BOUNDED_VALUE, CHOICE }

    final class Setting {
        public final String key;
        public final SettingType type;
        public final String defaultValue;
        public final String[] allowedValues;

        public Setting(String key, SettingType type, String defaultValue, String[] allowedValues) {
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
            this.allowedValues = allowedValues;
        }
    }

    String id();
    int nameResId();
    int descriptionResId();
    String settingsCategory();
    boolean hasHomeAction();
    boolean requiresRoot();
    Setting[] settings();
    String[] ownedProperties();
    String[] ownedFiles();
    String[] conflicts();
    InfoRow[] infoRows();
    int[] notes();
    ModuleAction[] actions();
    Warning[] enableWarnings();
    RestartRequirement restartRequirement();
    String checkCompatibility();
    String checkConflicts();
    ModuleStatus apply();
    ModuleStatus revert();
    ModuleStatus status();
}
