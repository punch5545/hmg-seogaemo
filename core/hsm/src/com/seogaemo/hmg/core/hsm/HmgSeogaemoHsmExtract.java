package com.seogaemo.hmg.core.hsm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Verifies and unpacks one module package for the root runtime.
 * Usage: extract ARCHIVE DEST SHA256. DEST must not exist. Every member is written
 * as a regular file, so archive symlinks can never become links on disk.
 */
public final class HmgSeogaemoHsmExtract {
    static final int MAX_ENTRIES = 256;
    static final long MAX_BYTES = 16L * 1024 * 1024;
    static final long MAX_ARCHIVE = 32L * 1024 * 1024;

    private HmgSeogaemoHsmExtract() { }

    public static void main(String[] args) {
        try {
            if (args.length != 4 || !"extract".equals(args[0])) throw new IOException("usage");
            long total = extract(new File(args[1]), new File(args[2]), args[3]);
            System.out.println("ok bytes=" + total);
        } catch (IOException e) {
            System.out.println("error " + e.getMessage());
            System.exit(1);
        }
    }

    static long extract(File archive, File dest, String expected) throws IOException {
        if (!expected.matches("[0-9a-f]{64}")) throw new IOException("bad digest argument");
        if (!archive.isFile() || archive.length() > MAX_ARCHIVE) throw new IOException("archive missing or too large");
        if (!expected.equals(sha256(archive))) throw new IOException("digest mismatch");
        if (dest.exists() || !dest.mkdir()) throw new IOException("destination unavailable");
        ZipFile zip = new ZipFile(archive);
        try {
            Set<String> seen = new HashSet<String>();
            long total = 0;
            int count = 0;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) throw new IOException("too many entries");
                String name = entry.getName();
                boolean directory = name.endsWith("/");
                String path = directory ? name.substring(0, name.length() - 1) : name;
                if (!allowed(path, directory)) throw new IOException("member not allowed: " + clean(name));
                if (!seen.add(path)) throw new IOException("duplicate member: " + clean(name));
                File target = new File(dest, path);
                if (directory) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new IOException("cannot create " + path);
                    continue;
                }
                File parent = target.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + path);
                total = copy(zip.getInputStream(entry), target, total);
            }
            if (!new File(dest, "module.properties").isFile()) throw new IOException("module.properties missing");
            return total;
        } finally {
            zip.close();
        }
    }

    static boolean allowed(String path, boolean directory) {
        if (!path.matches("[A-Za-z0-9._/-]+") || path.startsWith("/") || path.endsWith("/")) return false;
        for (String part : path.split("/", -1)) {
            if (part.length() == 0 || ".".equals(part) || "..".equals(part)) return false;
        }
        if (directory) return "strings".equals(path) || "files".equals(path) || path.startsWith("files/");
        if (path.startsWith("files/")) return true;
        if (path.matches("strings/[a-z]{2}(-[A-Z]{2})?\\.txt")) return true;
        // module.sh is accepted here and refused later unless the package is bundled with raw_shell=1.
        return path.equals("module.properties") || path.equals("apply.sh") || path.equals("check.sh") || path.equals("module.sh")
                || path.equals("status.sh") || path.equals("ui.txt") || path.equals("README.md");
    }

    static long copy(InputStream in, File target, long total) throws IOException {
        OutputStream out = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) throw new IOException("package larger than 16 MiB");
                out.write(buffer, 0, read);
            }
            return total;
        } finally {
            out.close();
            in.close();
        }
    }

    static String sha256(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable");
        } finally {
            in.close();
        }
    }

    static String clean(String value) {
        String result = value.replaceAll("[^A-Za-z0-9._/-]", "?");
        return result.length() > 80 ? result.substring(0, 80) : result;
    }
}
