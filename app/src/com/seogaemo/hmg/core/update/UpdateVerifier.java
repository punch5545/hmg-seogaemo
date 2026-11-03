package com.seogaemo.hmg.core.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Runs in the installed APK, also callable by root through app_process. */
public final class UpdateVerifier {
    private static final String KEY =
        "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA1ZlCsj0ehufSFrbXlmtb" +
        "glatSTLKLP/cBjyUD5ID5DxzpQBUuyqH/Y+JYFeFB3cBe5Rl11CIbvXu4aek6eTI" +
        "TjPh05OpXIXCySxTz5D++SCbamNl6uhyg6kdybrBux4VDIKsR6qjuUW58nu//RnF" +
        "3hjjTBpMlYjEZdeP+L/dJOfwOe1g6Bb+OwG3+wtySR7my3agVlyBBZYdP7V/2WO5" +
        "+V8v1rVWirr19fhz3tVwo8FtZ+ZUxbypLfSsT6SPXjGrtINpCD+t+pHW6hObTWqr" +
        "yPiTyZh0jXeBDzGObSDEpfjXDs2NcTHsRncxuDz6IQprnC+F44zy5dkw0AprEhS7" +
        "wcgbmFPEZ/yUrVIfOohD6VdpO6g9RoeeoJD1r0sjE530IBR/EPUf1IFddlq/Rqaa" +
        "VDyFwIvlY3lIJJp+5kZysiLTii0/wqFqLnzIF7PPx7fmaypjJMlXZdw0DbmESXun" +
        "xZZc1Nqz6L2Gsn/+N6C2uldPKjhQ91MV3TgpRUPyEBSLAgMBAAE=";
    private static final String VERSION = "[0-9]+\\.[0-9]+\\.[0-9]+";
    private static final String ASSET = "\\{\"name\":\"([A-Za-z0-9_.-]+)\",\"sha256\":\"([0-9a-f]{64})\",\"size\":([0-9]+)\\}";
    private static final Pattern MANIFEST = Pattern.compile("\\{\"assets\":\\[((?:" + ASSET + ",){3}" + ASSET + ")\\],\"schema\":1,\"target\":\\{\"androidApi\":19,\"models\":\"neev_kr\",\"os\":\"NEEV.KOR.\\*.260902\"\\},\"version\":\"(" + VERSION + ")\",\"versionCode\":([0-9]+)\\}\\n");
    private static final Pattern ASSET_PATTERN = Pattern.compile(ASSET);
    private static final long MAX_ZIP = 200L * 1024 * 1024;
    private static final long MAX_ENTRY = 100L * 1024 * 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private UpdateVerifier() {}

    public static String verify(File directory, String installed) throws Exception {
        return verify(directory, installed, KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodeBase64(KEY))));
    }

    /** Key parameter supports host-side signature tests; production always uses the embedded key. */
    public static String verify(File directory, String installed, PublicKey key) throws Exception {
        if (!installed.matches(VERSION)) throw new IOException("invalid installed version");
        File[] manifests = directory.listFiles();
        if (manifests == null) throw new IOException("missing staging directory");
        File manifest = null;
        for (File file : manifests)
            if (file.getName().matches("hmg-seogaemo-" + VERSION + "-manifest.json")) {
                if (manifest != null) throw new IOException("multiple manifests");
                manifest = file;
            }
        if (manifest == null || manifest.length() > 16384) throw new IOException("missing or oversized manifest");
        byte[] bytes = readBounded(manifest, 16384);
        byte[] sig = readBounded(new File(directory, manifest.getName() + ".sig"), 1024);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(bytes);
        if (!verifier.verify(sig)) throw new IOException("manifest signature invalid");
        Matcher parsed = MANIFEST.matcher(new String(bytes, UTF8));
        if (!parsed.matches()) throw new IOException("manifest format or target invalid");
        String version = parsed.group(8);
        if (!manifest.getName().equals("hmg-seogaemo-" + version + "-manifest.json")) throw new IOException("manifest filename mismatch");
        String[] parts = version.split("\\.");
        long major = Long.parseLong(parts[0]), minor = Long.parseLong(parts[1]), patch = Long.parseLong(parts[2]);
        if (minor >= 1000 || patch >= 1000 || major * 1000000L + minor * 1000L + patch != Long.parseLong(parsed.group(9))) throw new IOException("version code mismatch");
        if (!isNewer(version, installed)) throw new IOException("rollback refused");
        String assets = parsed.group(1);
        Matcher item = ASSET_PATTERN.matcher(assets);
        Set<String> found = new HashSet<String>();
        int end = 0;
        while (item.find()) {
            if (item.start() != end && !(item.start() == end + 1 && assets.charAt(end) == ',')) throw new IOException("invalid assets");
            end = item.end();
            String name = item.group(1);
            if (!found.add(name)) throw new IOException("duplicate asset");
            if (!name.startsWith("hmg-seogaemo-" + version + "-") && !name.equals("hmg-seogaemo-" + version + ".apk")) throw new IOException("asset version mismatch");
            if (name.endsWith("-ota.zip")) {
                File zip = new File(directory, name);
                long size = Long.parseLong(item.group(3));
                if (size > MAX_ZIP || zip.length() != size || !hex(sha256(zip)).equals(item.group(2))) throw new IOException("OTA hash mismatch");
            }
        }
        if (end != assets.length() || found.size() != 4 || !found.contains("hmg-seogaemo-" + version + "-ota.zip") || !found.contains("hmg-seogaemo-" + version + "-nemesis-package.zip") || !found.contains("hmg-seogaemo-" + version + "-nemesis-usb.zip") || !found.contains("hmg-seogaemo-" + version + ".apk")) throw new IOException("asset list invalid");
        return version;
    }

    public static void extract(File directory, String installed, File dest) throws Exception {
        extract(directory, installed, dest, KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodeBase64(KEY))));
    }

    public static void extract(File directory, String installed, File dest, PublicKey key) throws Exception {
        String version = verify(directory, installed, key);
        if (dest.exists() || !dest.mkdir()) throw new IOException("destination must be new");
        ZipInputStream zip = new ZipInputStream(new FileInputStream(new File(directory, "hmg-seogaemo-" + version + "-ota.zip")));
        TreeMap<String, String> md5s = new TreeMap<String, String>();
        try {
            Set<String> names = new HashSet<String>();
            long total = 0;
            int entries = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (++entries > 2048 || name.length() > 160 || !names.add(name) || !name.matches("(?:VERSION|update_roots\\.pem|modules\\.list|modules\\.graph|modules\\.lines|app/hmg-seogaemo\\.apk|(?:bin|lib|modules|core|svc|hookpoints|hookpoints\\.required|maps)/[A-Za-z0-9_./-]+)") || name.contains("..") || name.startsWith("/")) throw new IOException("unsafe ZIP entry");
                File out = new File(dest, name);
                if (entry.isDirectory()) { if (!out.isDirectory() && !out.mkdirs()) throw new IOException("mkdir failed"); continue; }
                if (!out.getParentFile().isDirectory() && !out.getParentFile().mkdirs()) throw new IOException("mkdir failed");
                FileOutputStream stream = new FileOutputStream(out);
                try {
                    byte[] buffer = new byte[8192];
                    int count;
                    long entrySize = 0;
                    MessageDigest md5 = MessageDigest.getInstance("MD5");
                    while ((count = zip.read(buffer)) != -1) {
                        entrySize += count;
                        total += count;
                        if (entrySize > MAX_ENTRY || total > MAX_ZIP) throw new IOException("ZIP expansion too large");
                        stream.write(buffer, 0, count);
                        md5.update(buffer, 0, count);
                    }
                    stream.getFD().sync();
                    md5s.put(name, hex(md5.digest()));
                } finally { stream.close(); }
            }
        } finally { zip.close(); }
        if (!new File(dest, "VERSION").isFile() || !version.equals(new String(readBounded(new File(dest, "VERSION"), 64), UTF8).trim()) || !new File(dest, "app/hmg-seogaemo.apk").isFile() || !new File(dest, "modules.graph").isFile()) throw new IOException("incomplete OTA payload");
        FileOutputStream list = new FileOutputStream(new File(dest, "files.md5"));
        try {
            for (java.util.Map.Entry<String, String> item : md5s.entrySet()) list.write((item.getValue() + "|" + item.getKey() + "\n").getBytes(UTF8));
            list.getFD().sync();
        } finally { list.close(); }
    }

    public static void copyIncoming(File input, File dest) throws Exception {
        if (!input.isDirectory() || dest.exists() || !dest.mkdir()) throw new IOException("invalid download copy");
        File[] files = input.listFiles();
        if (files == null) throw new IOException("download missing");
        int count = 0;
        for (File file : files) {
            String name = file.getName();
            if ("payload".equals(name) && file.isDirectory()) continue;
            if (!file.isFile() || !name.matches("hmg-seogaemo-" + VERSION + "-(?:manifest\\.json(?:\\.sig)?|ota\\.zip)")) throw new IOException("unexpected download file");
            long limit = name.endsWith("-ota.zip") ? MAX_ZIP : name.endsWith(".sig") ? 1024 : 16384;
            if (file.length() > limit) throw new IOException("download too large");
            InputStream in = new FileInputStream(file);
            FileOutputStream out = new FileOutputStream(new File(dest, name));
            try {
                byte[] buffer = new byte[8192];
                long size = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    size += n;
                    if (size > limit) throw new IOException("download too large");
                    out.write(buffer, 0, n);
                }
                out.getFD().sync();
            } finally { in.close(); out.close(); }
            count++;
        }
        if (count != 3) throw new IOException("incomplete download");
    }

    public static boolean isNewer(String a, String b) {
        if (!a.matches(VERSION) || !b.matches(VERSION)) return false;
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < 3; i++) {
            long n = Long.parseLong(x[i]), m = Long.parseLong(y[i]);
            if (n != m) return n > m;
        }
        return false;
    }
    private static byte[] readBounded(File file, int max) throws IOException {
        if (file.length() > max) throw new IOException("file too large");
        InputStream in = new FileInputStream(file);
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) >= 0) { out.write(buffer, 0, n); if (out.size() > max) throw new IOException("file too large"); }
            return out.toByteArray();
        } finally { in.close(); }
    }
    private static byte[] sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(file);
        try { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) >= 0) digest.update(b, 0, n); }
        finally { in.close(); }
        return digest.digest();
    }
    private static String hex(byte[] bytes) {
        char[] chars = "0123456789abcdef".toCharArray();
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) { out.append(chars[(b & 255) >>> 4]); out.append(chars[b & 15]); }
        return out.toString();
    }
    private static byte[] decodeBase64(String value) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int bits = 0, count = 0;
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '=') break;
            int digit = alphabet.indexOf(c);
            if (digit < 0) throw new IOException("invalid key");
            bits = (bits << 6) | digit;
            count += 6;
            if (count >= 8) { count -= 8; out.write((bits >> count) & 255); }
        }
        return out.toByteArray();
    }
    public static void main(String[] args) {
        try {
            if (args.length < 3 || args.length > 4) throw new IOException("usage");
            if ("verify".equals(args[0]) && args.length == 3) System.out.println(verify(new File(args[1]), args[2]));
            else if ("extract".equals(args[0]) && args.length == 4) extract(new File(args[1]), args[2], new File(args[3]));
            else if ("switch".equals(args[0]) && args.length == 3) {
                if (!new File(args[1]).renameTo(new File(args[2]))) throw new IOException("pointer rename failed");
            }
            else if ("copy".equals(args[0]) && args.length == 3) copyIncoming(new File(args[1]), new File(args[2]));
            else throw new IOException("usage");
        } catch (Exception e) { System.err.println(e.getMessage()); System.exit(1); }
    }
}
