package com.seogaemo.hmg.module;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class GeneratedModules {
    static final Module[] ALL = new Module[0];
    static final String[] IDS = {"wifi-client", "wireless-carplay-and-aa"};
    static final String[][] DEPENDS = {{}, {}};
    static final String[][] EXCLUDES = {{"wireless-carplay-and-aa"}, {"wifi-client"}};
}

public class InstalledModuleTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    static RootResult describe(String... lines) {
        Map<String, String> values = new HashMap<String, String>();
        for (String line : lines) {
            int eq = line.indexOf('=');
            values.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return new RootResult("on", "none", "Module description", 0, values);
    }

    static String plan(String id, boolean enable, Set<String> on) {
        String[] order = ModuleRegistry.orderedIds();
        Map<String, String[]> deps = new HashMap<String, String[]>(), exc = new HashMap<String, String[]>();
        for (String candidate : order) {
            deps.put(candidate, ModuleRegistry.depends(candidate));
            exc.put(candidate, ModuleRegistry.excludes(candidate));
        }
        String result = "";
        for (ModulePlan.Step step : ModulePlanner.plan(id, enable, order, deps, exc, on).steps)
            result += (step.enable ? "+" : "-") + step.id + " ";
        return result.trim();
    }

    public static void main(String[] args) {
        InstalledModule sample = InstalledModule.parse("sample", describe(
                "info.version=1.0.0", "info.source=direct", "info.restart=head_unit", "info.restore=exact",
                "info.depends=", "info.excludes=wifi-client", "info.uses=wifi.radio:ap,audio.out",
                "info.setting_1=audio:toggle:off", "info.setting_2=mode:choice(fast|safe):safe",
                "info.setting_10=level:number(1..5):3", "info.setting_3=bad:choice(a|b):c",
                "info.ui_2=toggle setting=audio label=@audio", "info.ui_10=info @how", "info.ui_1=status",
                "info.str_name=샘플", "info.str_audio=Audio"));
        check(sample != null, "valid description rejected");
        check(sample.version.equals("1.0.0") && sample.restart.equals("head_unit") && !sample.rebootRestore, "manifest");
        check(Arrays.equals(sample.uses, new String[] {"wifi.radio:ap", "audio.out"}), "uses");
        check(sample.depends.length == 0 && Arrays.equals(sample.excludes, new String[] {"wifi-client"}), "relations");
        check(sample.settings.size() == 3, "invalid setting kept: " + sample.settings.size());
        check(sample.settings.get(0).type == Module.SettingType.BOOLEAN, "toggle");
        check(sample.settings.get(1).type == Module.SettingType.CHOICE &&
                Arrays.equals(sample.settings.get(1).allowedValues, new String[] {"fast", "safe"}), "choice");
        check(sample.settings.get(2).key.equals("level") && sample.settings.get(2).type == Module.SettingType.BOUNDED_VALUE &&
                Arrays.equals(sample.settings.get(2).allowedValues, new String[] {"1", "5"}), "number after 10 ordering");
        check(sample.uiBlocks.equals(Arrays.asList("status", "toggle setting=audio label=@audio", "info @how")), "ui order " + sample.uiBlocks);
        check(sample.name().equals("샘플") && sample.text("@audio").equals("Audio") && sample.summary().equals("summary"), "strings");

        check(InstalledModule.parse("sample", new RootResult("failed", "none", "x", 1, null)) == null, "failure accepted");
        check(InstalledModule.parse("Bad_Id", describe("info.version=1.0.0")) == null, "bad id accepted");
        check(InstalledModule.parse("sample", describe("info.source=direct")) == null, "missing version accepted");
        check(InstalledModule.setting("x:number(5..1):3") == null && InstalledModule.setting("x:toggle:yes") == null &&
                InstalledModule.setting("X:toggle:on") == null && InstalledModule.setting("x:number(1..5):9") == null, "bad settings");

        check(RootClient.validId("wireless-adb") && !RootClient.validId("a--b") && !RootClient.validId("a b") &&
                !RootClient.validId("-a") && !RootClient.validId(null), "id validation");
        check(RootClient.moduleSet("sample", "mode", "fast;reboot").exitCode == 1, "free-form setting value");
        check(RootClient.moduleSet("sample", "Mode", "fast").exitCode == 1, "bad setting key");
        check(RootClient.moduleDescribe("sample", "kor").exitCode == 1, "bad locale");
        check(RootClient.moduleInstall("../x").exitCode == 1, "bad digest");
        check(RootClient.run("x; rm -rf /", "status").exitCode == 1, "shell in id");

        check(Arrays.equals(ModuleRegistry.orderedIds(), new String[] {"wifi-client", "wireless-carplay-and-aa"}), "built-ins only");
        InstalledModule dependent = InstalledModule.parse("addon", describe("info.version=0.1.0", "info.depends=sample"));
        ModuleRegistry.setInstalled(Arrays.asList(sample, dependent));
        check(Arrays.equals(ModuleRegistry.orderedIds(),
                new String[] {"wifi-client", "wireless-carplay-and-aa", "sample", "addon"}), "installed order");
        check(Arrays.equals(ModuleRegistry.depends("addon"), new String[] {"sample"}), "installed depends");
        check(Arrays.asList(ModuleRegistry.excludes("wifi-client")).contains("sample"), "reverse exclusion missing");
        check(Arrays.asList(ModuleRegistry.excludes("wifi-client")).contains("wireless-carplay-and-aa"), "built-in exclusion lost");

        Set<String> on = new HashSet<String>(Arrays.asList("sample", "addon"));
        check(plan("wifi-client", true, on).equals("-addon -sample +wifi-client"), plan("wifi-client", true, on));
        check(plan("addon", true, new HashSet<String>()).equals("+sample +addon"), plan("addon", true, new HashSet<String>()));
        check(plan("sample", false, on).equals("-addon -sample"), plan("sample", false, on));
        System.out.println("InstalledModuleTest ok");
    }
}
