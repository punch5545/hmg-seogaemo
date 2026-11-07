package hmgapi.mods.wireless;

/** Region branches of the Phone Projection menu hook. */
public final class ProjectionMenuTest {
    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }

    public static void main(String[] args) {
        for (boolean dc : new boolean[] {false, true}) {
            check(ProjectionMenu.target(0, dc) == ProjectionMenu.OTHER_PAGE, "KR opens the other page, dc=" + dc);
            check(ProjectionMenu.target(-1, dc) == ProjectionMenu.STOCK, "unknown region keeps stock, dc=" + dc);
            check(ProjectionMenu.target(2, dc) == ProjectionMenu.STOCK, "other region keeps stock, dc=" + dc);
        }
        check(ProjectionMenu.target(1, true) == ProjectionMenu.DEVICE_CONNECTIONS, "US device connections");
        check(ProjectionMenu.target(4, true) == ProjectionMenu.DEVICE_CONNECTIONS, "Canada device connections");
        check(ProjectionMenu.target(1, false) == ProjectionMenu.OTHER_PAGE, "US other page");
        check(ProjectionMenu.target(4, false) == ProjectionMenu.OTHER_PAGE, "Canada other page");
        System.out.println("ok phone projection menu region choice");
    }
}
