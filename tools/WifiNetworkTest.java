package com.seogaemo.hmg.module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WifiNetworkTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        // IEEE 802.11i-2004 Annex H.4.1 test vector.
        check(WifiNetwork.psk("password", WifiNetwork.bytes("IEEE"))
                .equals("f42c6fc52df0ebef9ebb4b90b38a5f902e83fe1b135a70e23aed762e9710a12e"), "psk vector");
        check(WifiNetwork.request(WifiNetwork.bytes("Cafe"), "").equals("ssid=43616665\npsk=none\n"), "open request");
        check(WifiNetwork.request(WifiNetwork.bytes("IEEE"), "password").equals(
                "ssid=49454545\npsk=f42c6fc52df0ebef9ebb4b90b38a5f902e83fe1b135a70e23aed762e9710a12e\n"), "psk request");
        try {
            WifiNetwork.request(WifiNetwork.bytes("IEEE"), "short");
            throw new AssertionError("short passphrase accepted");
        } catch (java.security.GeneralSecurityException expected) { }
        check(!WifiNetwork.validPassphrase("1234567") && WifiNetwork.validPassphrase("12345678"), "length");
        check(!WifiNetwork.validPassphrase("abcdefgh\u00e9"), "non-ASCII passphrase");
        check(WifiNetwork.validName("\uc9d1 Wi-Fi") && !WifiNetwork.validName("") &&
                !WifiNetwork.validName("123456789012345678901234567890123"), "name length");

        Map<String, String> info = new HashMap<String, String>();
        info.put("net_1", "-70,open,Cafe Free");
        info.put("net_2", "-41,psk,\\xec\\xa7\\x91 \\\"5G\\\"");
        info.put("net_3", "-60,eap,Office");
        info.put("net_4", "x,psk,Bad");
        info.put("count", "4");
        List<WifiNetwork> networks = WifiNetwork.parse(info);
        check(networks.size() == 2, "supported only: " + networks.size());
        check(networks.get(0).name.equals("\uc9d1 \"5G\"") && networks.get(0).needsPassword(), networks.get(0).name);
        check(WifiNetwork.hex(networks.get(0).ssid).equals("ec a7 91 20 22 35 47 22".replace(" ", "")), "ssid bytes");
        check(networks.get(1).name.equals("Cafe Free") && !networks.get(1).needsPassword(), "open network");
        System.out.println("wifi network tests passed");
    }
}
