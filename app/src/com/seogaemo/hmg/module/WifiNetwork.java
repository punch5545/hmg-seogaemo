package com.seogaemo.hmg.module;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * A network seen by the USB Wi-Fi adapter. The wifi module's wifi_scan action reports
 * info.net_N=signal,security,ssid (ssid escaped by wpa_cli); its wifi_join action reads
 * the request written by {@link #request} from the app's files/wifi/usb-join.
 */
public final class WifiNetwork {
    public final String name;
    public final byte[] ssid;
    public final int signal;
    public final String security;

    WifiNetwork(byte[] ssid, int signal, String security) {
        this.ssid = ssid;
        this.name = utf8(ssid);
        this.signal = signal;
        this.security = security;
    }

    public boolean supported() { return "psk".equals(security) || "open".equals(security); }

    public boolean needsPassword() { return "psk".equals(security); }

    public static List<WifiNetwork> parse(Map<String, String> info) {
        List<WifiNetwork> networks = new ArrayList<WifiNetwork>();
        for (Map.Entry<String, String> entry : info.entrySet()) {
            if (!entry.getKey().matches("net_[0-9]+")) continue;
            String[] part = entry.getValue().split(",", 3);
            if (part.length < 3 || part[2].length() == 0) continue;
            int signal;
            try { signal = Integer.parseInt(part[0]); } catch (NumberFormatException bad) { continue; }
            WifiNetwork network = new WifiNetwork(unescape(part[2]), signal, part[1]);
            if (network.supported()) networks.add(network);
        }
        Collections.sort(networks, new Comparator<WifiNetwork>() {
            public int compare(WifiNetwork a, WifiNetwork b) { return b.signal - a.signal; }
        });
        return networks;
    }

    /** Reverses wpa_cli's printf_encode: \\, \", \e, \n, \r, \t and \xNN. */
    static byte[] unescape(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] in = bytes(text);
        for (int i = 0; i < in.length; i++) {
            if (in[i] != '\\' || i + 1 >= in.length) { out.write(in[i]); continue; }
            char next = (char) in[++i];
            if (next == 'n') out.write('\n');
            else if (next == 'r') out.write('\r');
            else if (next == 't') out.write('\t');
            else if (next == 'e') out.write(27);
            else if (next == 'x' && i + 2 < in.length) {
                try {
                    out.write(Integer.parseInt(new String(in, i + 1, 2, "US-ASCII"), 16));
                    i += 2;
                } catch (Exception bad) { out.write('\\'); out.write('x'); }
            } else out.write(next);
        }
        return out.toByteArray();
    }

    public static boolean validName(String name) {
        int length = bytes(name).length;
        return length >= 1 && length <= 32;
    }

    /** WPA passphrases are 8 to 63 printable ASCII characters. */
    public static boolean validPassphrase(String passphrase) {
        if (passphrase.length() < 8 || passphrase.length() > 63) return false;
        for (int i = 0; i < passphrase.length(); i++) {
            char c = passphrase.charAt(i);
            if (c < 0x20 || c > 0x7e) return false;
        }
        return true;
    }

    /** The join request: the SSID as hex and the WPA key derived here, or "none" for an open network. */
    public static String request(byte[] ssid, String passphrase) throws GeneralSecurityException {
        String key = passphrase.length() == 0 ? "none" : psk(passphrase, ssid);
        return "ssid=" + hex(ssid) + "\npsk=" + key + "\n";
    }

    /** IEEE 802.11i pre-shared key: PBKDF2-HMAC-SHA1(passphrase, SSID, 4096 rounds, 256 bits). */
    static String psk(String passphrase, byte[] ssid) throws GeneralSecurityException {
        if (!validPassphrase(passphrase)) throw new GeneralSecurityException("invalid passphrase");
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), ssid, 4096, 256);
        return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded());
    }

    static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return out.toString();
    }

    public static byte[] bytes(String text) {
        try { return text.getBytes("UTF-8"); }
        catch (UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String utf8(byte[] value) {
        try { return new String(value, "UTF-8"); }
        catch (UnsupportedEncodingException impossible) { throw new IllegalStateException(impossible); }
    }
}
