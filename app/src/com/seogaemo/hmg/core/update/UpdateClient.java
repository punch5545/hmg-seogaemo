package com.seogaemo.hmg.core.update;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.seogaemo.hmg.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** GitHub is a transport. UpdateVerifier authenticates downloaded bytes. */
public final class UpdateClient {
    public interface Progress { void changed(int phase); }
    public static final class NoUpdateException extends IOException {
        public NoUpdateException() { super("no newer release"); }
    }
    private static final String API = "https://api.github.com/repos/seogaemo/hmg-seogaemo/releases/latest";
    private static final int API_LIMIT = 512 * 1024;
    private static final int ZIP_LIMIT = 200 * 1024 * 1024;
    private final Context context;
    private final SSLSocketFactory tls;

    public UpdateClient(Context context) throws Exception {
        this.context = context;
        InputStream roots = context.getResources().openRawResource(R.raw.update_roots);
        SSLContext ssl = SSLContext.getInstance("TLS");
        try { ssl.init(null, new javax.net.ssl.TrustManager[] {UpdateTrust.create(roots)}, null); }
        finally { roots.close(); }
        tls = new Tls12Factory(ssl.getSocketFactory());
    }

    public boolean hasUsableNetwork() {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
        return info != null && info.isConnected() && (info.getType() == ConnectivityManager.TYPE_WIFI || info.getType() == ConnectivityManager.TYPE_ETHERNET);
    }

    public String download(String installed, Progress progress) throws Exception {
        if (!hasUsableNetwork()) throw new IOException("Wi-Fi or Ethernet connection required");
        progress.changed(R.string.update_checking);
        File dir = new File(context.getFilesDir(), "update");
        if (dir.exists()) {
            File[] old = dir.listFiles();
            if (old != null) for (File file : old) deleteTree(file);
        } else if (!dir.mkdir()) throw new IOException("cannot create download directory");
        byte[] metadata = fetch(API, API_LIMIT, null);
        JSONObject release = new JSONObject(new String(metadata, "UTF-8"));
        String tag = release.getString("tag_name");
        if (!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IOException("invalid release tag");
        String version = tag.substring(1);
        if (!UpdateVerifier.isNewer(version, installed)) throw new NoUpdateException();
        String prefix = "hmg-seogaemo-" + version;
        JSONArray assets = release.getJSONArray("assets");
        String[] wanted = {prefix + "-manifest.json", prefix + "-manifest.json.sig", prefix + "-ota.zip"};
        int[] limits = {16384, 1024, ZIP_LIMIT};
        for (int i = 0; i < wanted.length; i++) {
            String url = null;
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.getJSONObject(j);
                if (wanted[i].equals(asset.getString("name"))) {
                    if (url != null) throw new IOException("duplicate release asset");
                    url = asset.getString("browser_download_url");
                }
            }
            if (url == null) throw new IOException("release asset missing: " + wanted[i]);
            progress.changed(i == 2 ? R.string.update_downloading : R.string.update_fetching_signature);
            fetch(url, limits[i], new File(dir, wanted[i]));
        }
        progress.changed(R.string.update_verifying);
        String verified = UpdateVerifier.verify(dir, installed);
        UpdateVerifier.extract(dir, installed, new File(dir, "payload"));
        return verified;
    }

    private static void deleteTree(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IOException("cannot clear old download");
    }

    private byte[] fetch(String address, int limit, File destination) throws Exception {
        URL url = new URL(address);
        for (int redirect = 0; redirect < 6; redirect++) {
            String host = url.getHost().toLowerCase(java.util.Locale.US);
            if (!"https".equals(url.getProtocol()) || !(host.equals("api.github.com") || host.equals("github.com") || host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com"))) throw new IOException("unapproved download host");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setSSLSocketFactory(tls);
            connection.setHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String host, SSLSession session) { return HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session); }
            });
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "HMG-Seogaemo-Updater");
            try {
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("redirect missing location");
                    url = new URL(url, location);
                    continue;
                }
                if (code != 200) throw new IOException("GitHub HTTP " + code);
                if (connection.getContentLength() > limit) throw new IOException("download too large");
                InputStream stream = connection.getInputStream();
                java.io.ByteArrayOutputStream memory = destination == null ? new java.io.ByteArrayOutputStream() : null;
                FileOutputStream file = destination == null ? null : new FileOutputStream(destination);
                try {
                    byte[] buffer = new byte[8192];
                    int count;
                    long total = 0;
                    while ((count = stream.read(buffer)) != -1) {
                        total += count;
                        if (total > limit) throw new IOException("download too large");
                        if (file != null) file.write(buffer, 0, count); else memory.write(buffer, 0, count);
                    }
                    if (file != null) file.getFD().sync();
                    return memory == null ? null : memory.toByteArray();
                } finally { stream.close(); if (file != null) file.close(); }
            } finally { connection.disconnect(); }
        }
        throw new IOException("too many redirects");
    }

    public static String root(String verb) throws Exception {
        if (!("stage".equals(verb) || "apply".equals(verb) || "rollback".equals(verb) || "status".equals(verb))) throw new IOException("invalid verb");
        Process process = new ProcessBuilder("/data/nemesis/apps/hmg-seogaemo/bin/nemesis-client", "root", "-c", "/system/bin/sh /data/nemesis/apps/hmg-seogaemo/bin/hmg-seogaemo-root update " + verb).redirectErrorStream(true).start();
        InputStream in = process.getInputStream();
        byte[] data = new byte[4096];
        int used = 0, n;
        while (used < data.length && (n = in.read(data, used, data.length - used)) >= 0) {
            used += n;
            if (used == data.length) throw new IOException("root output too large");
        }
        int code = process.waitFor();
        String result = new String(data, 0, used, "UTF-8").trim();
        if (code != 0) throw new IOException(result.length() == 0 ? "root operation failed" : result);
        return result;
    }

    private static final class Tls12Factory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        Tls12Factory(SSLSocketFactory delegate) { this.delegate = delegate; }
        private java.net.Socket enable(java.net.Socket socket) { if (socket instanceof SSLSocket) ((SSLSocket) socket).setEnabledProtocols(new String[] {"TLSv1.2"}); return socket; }
        public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        public java.net.Socket createSocket(java.net.Socket s, String h, int p, boolean auto) throws IOException { return enable(delegate.createSocket(s, h, p, auto)); }
        public java.net.Socket createSocket(String h, int p) throws IOException { return enable(delegate.createSocket(h, p)); }
        public java.net.Socket createSocket(String h, int p, java.net.InetAddress l, int lp) throws IOException { return enable(delegate.createSocket(h, p, l, lp)); }
        public java.net.Socket createSocket(java.net.InetAddress h, int p) throws IOException { return enable(delegate.createSocket(h, p)); }
        public java.net.Socket createSocket(java.net.InetAddress h, int p, java.net.InetAddress l, int lp) throws IOException { return enable(delegate.createSocket(h, p, l, lp)); }
    }
}
