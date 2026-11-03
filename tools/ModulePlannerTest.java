import com.seogaemo.hmg.module.ModulePlan;
import com.seogaemo.hmg.module.ModulePlanner;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ModulePlannerTest {
    private static void expect(ModulePlan plan, String wanted) {
        StringBuilder actual = new StringBuilder();
        for (ModulePlan.Step step : plan.steps)
            actual.append(step.enable ? '+' : '-').append(step.id).append(' ');
        if (!wanted.equals(actual.toString()))
            throw new AssertionError(actual + " != " + wanted);
    }

    public static void main(String[] args) {
        String[] order = {"base", "dependent", "other"};
        Map<String, String[]> deps = new HashMap<String, String[]>();
        deps.put("base", new String[0]);
        deps.put("dependent", new String[] {"base"});
        deps.put("other", new String[0]);
        Map<String, String[]> excludes = new HashMap<String, String[]>();
        excludes.put("base", new String[] {"other"});
        excludes.put("dependent", new String[0]);
        excludes.put("other", new String[] {"base"});
        Set<String> none = new HashSet<String>();
        expect(ModulePlanner.plan("dependent", true, order, deps, excludes, none),
                "+base +dependent ");
        Set<String> on = new HashSet<String>(Arrays.asList("base", "dependent"));
        expect(ModulePlanner.plan("other", true, order, deps, excludes, on),
                "-dependent -base +other ");
        expect(ModulePlanner.plan("base", false, order, deps, excludes, on),
                "-dependent -base ");
        excludes.put("other", new String[0]);
        excludes.put("base", new String[0]);
        if (!ModulePlanner.related("dependent", order, deps, excludes)
                .equals(new HashSet<String>(Arrays.asList("dependent", "base"))))
            throw new AssertionError("related dependency chain");
        if (!ModulePlanner.related("other", order, deps, excludes)
                .equals(new HashSet<String>(Arrays.asList("other"))))
            throw new AssertionError("unrelated module included");
        excludes.put("other", new String[] {"dependent"});
        if (!ModulePlanner.related("base", order, deps, excludes)
                .equals(new HashSet<String>(Arrays.asList("base", "dependent", "other"))))
            throw new AssertionError("transitive exclusion missing");
    }
}
