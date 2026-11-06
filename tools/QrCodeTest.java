import com.seogaemo.hmg.core.IssueLink;
import com.seogaemo.hmg.ui.QrCode;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** QR matrices match fixtures from an independent encoder (python-qrcode, level M, byte mode, fixed mask). */
public class QrCodeTest {
    public static void main(String[] args) throws Exception {
        File[] fixtures = new File(args[0]).listFiles();
        int checked = 0;
        for (File fixture : fixtures) {
            if (!fixture.getName().endsWith(".txt")) continue;
            List<String> lines = new ArrayList<String>();
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(fixture), "UTF-8"));
            try { for (String line; (line = in.readLine()) != null; ) lines.add(line); } finally { in.close(); }
            String[] header = lines.get(1).split(" ");
            QrCode code = QrCode.encode(lines.get(0).getBytes("UTF-8"), Integer.parseInt(header[1]));
            check(code.version == Integer.parseInt(header[0]), fixture + ": version " + code.version);
            check(code.size == lines.size() - 2, fixture + ": size");
            for (int y = 0; y < code.size; y++)
                for (int x = 0; x < code.size; x++)
                    check(code.get(x, y) == (lines.get(y + 2).charAt(x) == '1'), fixture + ": module " + x + "," + y);
            checked++;
        }
        check(checked >= 3, "fixtures missing");

        QrCode auto = QrCode.encode("https://github.com/seogaemo/hmg-seogaemo/issues/new");
        check(auto.mask >= 0 && auto.mask < 8, "mask");

        String link = IssueLink.build(IssueLink.TEMPLATE_MAP, "Map: X", "product", "NEEV.KOR.0000", "onebin", "",
                "map-status", "state=on\nfailing=a,b", "modules", "ê\u0001ok");
        check(link.equals(IssueLink.NEW_ISSUE + "?template=new-firmware-map.yml&title=Map%3A%20X&product=NEEV.KOR.0000"
                + "&map-status=state%3Don%0Afailing%3Da%2Cb&modules=ok"), link);
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 1000; i++) huge.append('a');
        String bounded = IssueLink.build(IssueLink.TEMPLATE_BUG, null, "a", huge.toString(), "b", huge.toString(),
                "c", huge.toString(), "d", "short");
        check(bounded.length() <= 700, "link length " + bounded.length());
        check(bounded.contains("&a=") && !bounded.contains("&d="), bounded);
        System.out.println("qr code and issue link checks passed");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
