package hmgapi.mods.wireless;

import android.net.wifi.WifiConfiguration;

/** The zygote snapshot of the wifi module's peer file and the values the USB hotspot hooks return. */
public final class UsbHotspotTest {
    static final String USB = "radio=usb\ninterface=rtwlan0\nip=192.168.50.1\nssid=HMG-Seogaemo-6f1d\n"
            + "passphrase=pass:word=1\nchannel=36\nready=0\n";

    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }

    static void stock(String text, String why) {
        String reason = UsbHotspot.use(UsbHotspot.parse(text));
        check(!UsbHotspot.active() && reason.startsWith("stock"), why + ": " + reason);
        check("wlan0".equals(UsbHotspot.gpmName("wlan0")) && "rtwlan0".equals(UsbHotspot.gpmName("rtwlan0")), why + ": names unchanged");
        try { UsbHotspot.apConfiguration(); check(false, why + ": configuration without a USB hotspot"); }
        catch (IllegalStateException expected) { }
    }

    public static void main(String[] args) {
        // No peer file on the host: the stock hotspot, nothing thrown.
        check(UsbHotspot.snapshot().startsWith("stock") && !UsbHotspot.active(), "missing file");

        String reason = UsbHotspot.use(UsbHotspot.parse(USB));
        check(UsbHotspot.active() && "rtwlan0".equals(UsbHotspot.iface()), "usb: " + reason);
        check(reason.equals("usb: rtwlan0 192.168.50.1"), "reason carries no credentials: " + reason);
        WifiConfiguration c = UsbHotspot.apConfiguration();
        check("HMG-Seogaemo-6f1d".equals(c.SSID) && "HMG-Seogaemo-6f1d".equals(c.ConnectivitySSID), "ssid");
        check("pass:word=1".equals(c.preSharedKey) && "pass:word=1".equals(c.ConnectivityPSK), "passphrase keeps '=' and ':'");
        check(c.allowedKeyManagement.cardinality() == 1 && c.allowedKeyManagement.get(4), "WPA2_PSK only");
        check(UsbHotspot.apConfiguration() != c, "a fresh configuration per call");
        // GPM's host locator: the adapter is "wlan0", the built-in radio is not.
        check("wlan0".equals(UsbHotspot.gpmName("rtwlan0")), "adapter as wlan0");
        check("wlan0-builtin".equals(UsbHotspot.gpmName("wlan0")), "built-in renamed");
        check("lo".equals(UsbHotspot.gpmName("lo")) && UsbHotspot.gpmName(null) == null, "others unchanged");
        // The first value of a key wins, as sg_peer_info reads it.
        check(UsbHotspot.parse("radio=usb\nradio=none\n").get("radio").equals("usb"), "first wins");

        stock(USB.replace("radio=usb", "radio=builtin"), "builtin");
        stock(USB.replace("radio=usb", "radio=none"), "none");
        stock(USB.replace("interface=rtwlan0", "interface=wlan0"), "stock interface");
        stock(USB.replace("interface=rtwlan0", "interface=rt wlan0"), "bad interface");
        stock(USB.replace("ip=192.168.50.1", "ip="), "no ip");
        stock(USB.replace("ssid=HMG-Seogaemo-6f1d\n", ""), "no ssid");
        stock(USB.replace("passphrase=pass:word=1", "passphrase=short"), "short passphrase");
        stock(USB.replace("passphrase=pass:word=1", "passphrase=tab\tinside"), "control character");
        stock("", "empty");
        // A valid snapshot followed by an invalid one leaves nothing behind.
        UsbHotspot.use(UsbHotspot.parse(USB));
        stock(USB.replace("ssid=HMG-Seogaemo-6f1d", "ssid=" + new String(new char[33]).replace('\0', 'x')), "long ssid");
        System.out.println("ok usb hotspot snapshot, configuration and GPM names");
    }
}
