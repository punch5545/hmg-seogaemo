package android.net.wifi;

/** Host stub: the AOSP fields plus Hyundai's ConnectivitySSID/ConnectivityPSK. */
public class WifiConfiguration {
    public String SSID, preSharedKey, ConnectivitySSID, ConnectivityPSK;
    public java.util.BitSet allowedKeyManagement = new java.util.BitSet();
}
