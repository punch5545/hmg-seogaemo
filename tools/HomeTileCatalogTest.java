import com.seogaemo.hmg.module.HomeTile;
import com.seogaemo.hmg.module.HomeTileCatalog;
import java.util.List;

public final class HomeTileCatalogTest {
    private static HomeTile tile(String id, int order, boolean alwaysVisible) {
        return new HomeTile(id, 1, id, null, order, alwaysVisible);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        HomeTile settings = tile("settings", 0, true);
        HomeTileCatalog.Entry[] entries = {
            new HomeTileCatalog.Entry("z-module", true, new HomeTile[] {
                tile("late", 20, false), tile("z-tie", 10, false) }),
            new HomeTileCatalog.Entry("hidden", false, new HomeTile[] {
                tile("hidden", -10, false), tile("always", 5, true) }),
            new HomeTileCatalog.Entry("a-module", true, new HomeTile[] {
                tile("a-tie", 10, false), tile("early", -5, false) })
        };
        List<HomeTile> tiles = HomeTileCatalog.collect(settings, entries);
        String[] expected = {"settings", "early", "always", "a-tie", "z-tie", "late"};
        check(tiles.size() == expected.length, "visibility count");
        for (int i = 0; i < expected.length; i++)
            check(expected[i].equals(tiles.get(i).id), "order at " + i);
        check(HomeTileCatalog.pageCount(1) == 1, "single Settings page");
        check(HomeTileCatalog.pageCount(6) == 1, "full first page");
        check(HomeTileCatalog.pageCount(7) == 2, "overflow page");
        check(HomeTileCatalog.pageCount(12) == 2, "full second page");
        check(HomeTileCatalog.pageCount(13) == 3, "third page");
        System.out.println("HomeTileCatalogTest OK");
    }
}
