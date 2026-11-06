package com.seogaemo.hmg.core.hsm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Writes a private copy of a stock program with one fixed string replaced, for the
 * runtime's sg_bin_derive. Usage: patch SOURCE DEST OLD NEW. NEW (not longer than OLD)
 * is padded with NUL bytes to OLD's length, so no offset in the program moves. Every
 * occurrence is replaced; a program without OLD is copied unchanged. DEST must not exist.
 */
public final class HmgSeogaemoBinPatch {
    static final long MAX_BYTES = 8L * 1024 * 1024;

    private HmgSeogaemoBinPatch() { }

    public static void main(String[] args) {
        try {
            if (args.length != 5 || !"patch".equals(args[0])) throw new IOException("usage");
            int count = patch(new File(args[1]), new File(args[2]), args[3], args[4]);
            System.out.println("ok count=" + count);
        } catch (IOException e) {
            System.out.println("error " + e.getMessage());
            System.exit(1);
        }
    }

    static int patch(File source, File dest, String oldText, String newText) throws IOException {
        if (!oldText.matches("/[a-zA-Z0-9_./-]{3,127}") || !newText.matches("[a-zA-Z0-9_.-]{1,128}")
                || newText.length() > oldText.length()) {
            throw new IOException("bad strings");
        }
        long size = source.length();
        if (!source.isFile() || size <= 0 || size > MAX_BYTES) throw new IOException("bad source");
        if (dest.exists()) throw new IOException("destination exists");
        byte[] data = new byte[(int) size];
        InputStream in = new FileInputStream(source);
        try {
            int done = 0;
            while (done < data.length) {
                int n = in.read(data, done, data.length - done);
                if (n < 0) throw new IOException("short read");
                done += n;
            }
            if (in.read() >= 0) throw new IOException("source changed");
        } finally {
            in.close();
        }
        byte[] needle = oldText.getBytes("US-ASCII");
        byte[] replacement = new byte[needle.length];
        byte[] text = newText.getBytes("US-ASCII");
        System.arraycopy(text, 0, replacement, 0, text.length);
        int count = 0;
        for (int i = 0; i + needle.length <= data.length; i++) {
            int j = 0;
            while (j < needle.length && data[i + j] == needle[j]) j++;
            if (j == needle.length) {
                System.arraycopy(replacement, 0, data, i, replacement.length);
                count++;
                i += needle.length - 1;
            }
        }
        FileOutputStream out = new FileOutputStream(dest);
        try {
            out.write(data);
            out.getFD().sync();
        } finally {
            out.close();
        }
        return count;
    }
}
