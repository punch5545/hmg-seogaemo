package com.seogaemo.hmg.core;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.zip.Deflater;

/**
 * GitHub "new issue" link with an issue form prefilled through query parameters, shown as a QR code
 * so a phone can open it. Only fixed, non-personal facts go in (firmware ids, versions, map and
 * module states, redacted logs); values are reduced to printable ASCII and the link stays short
 * enough to scan from the head-unit screen. A log goes in packed (raw deflate, base64url, "HSR1."
 * prefix); the issue workflow and tools/decode-issue-log.py turn it back into text.
 */
public final class IssueLink {
    public static final String NEW_ISSUE = "https://github.com/seogaemo/hmg-seogaemo/issues/new";
    public static final String TEMPLATE_BUG = "bug-car-support.yml";
    public static final String TEMPLATE_MAP = "new-firmware-map.yml";
    static final int MAX_VALUE = 600;
    public static final String PACK_PREFIX = "HSR1.";
    private static final char[] BASE64URL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private IssueLink() { }

    /**
     * fields alternate form field id and value; empty values are left out. Fields keep their order;
     * the first one that no longer fits is cut short and later ones are dropped, so put logs last.
     */
    public static String build(String template, String title, int maxLink, String... fields) {
        StringBuilder link = new StringBuilder(NEW_ISSUE).append("?template=").append(encode(template));
        if (title != null && title.length() > 0) link.append("&title=").append(encode(clean(title)));
        for (int i = 0; i + 1 < fields.length; i += 2) {
            String value = clean(fields[i + 1]);
            if (value.length() == 0) continue;
            String head = "&" + encode(fields[i]) + "=";
            String part = head + encode(value);
            while (link.length() + part.length() > maxLink && value.length() > 0) {
                value = value.substring(0, value.length() - 1);
                part = head + encode(value);
            }
            if (value.trim().length() == 0) break;
            link.append(part);
            if (value.length() < clean(fields[i + 1]).length()) break;
        }
        return link.toString();
    }

    /**
     * Appends field=packed log, keeping as many leading lines as fit in maxLink; a cut log ends with
     * a line counting what was left out. Unchanged when not even the first line fits.
     */
    public static String withLog(String link, String field, String text, int maxLink) {
        String[] lines = text.split("\n");
        int room = maxLink - link.length() - field.length() - 2;
        String best = null;
        int low = 1, high = lines.length;
        while (low <= high) {
            int count = (low + high) >>> 1;
            String packed = pack(lines, count);
            if (packed.length() <= room) { best = packed; low = count + 1; }
            else high = count - 1;
        }
        return best == null ? link : link + "&" + field + "=" + best;
    }

    static String pack(String[] lines, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) text.append('\n');
            text.append(lines[i]);
        }
        if (count < lines.length) text.append("\n[").append(lines.length - count).append(" more lines in the USB export]");
        return PACK_PREFIX + base64url(deflate(text.toString()));
    }

    private static byte[] deflate(String text) {
        byte[] input;
        try {
            input = text.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(input);
        deflater.finish();
        byte[] buffer = new byte[input.length + 64];
        int length = 0;
        while (!deflater.finished()) {
            if (length == buffer.length) {
                byte[] grown = new byte[buffer.length * 2];
                System.arraycopy(buffer, 0, grown, 0, length);
                buffer = grown;
            }
            length += deflater.deflate(buffer, length, buffer.length - length);
        }
        deflater.end();
        byte[] out = new byte[length];
        System.arraycopy(buffer, 0, out, 0, length);
        return out;
    }

    static String base64url(byte[] data) {
        StringBuilder out = new StringBuilder((data.length + 2) / 3 * 4);
        for (int i = 0; i < data.length; i += 3) {
            int n = (data[i] & 0xFF) << 16;
            if (i + 1 < data.length) n |= (data[i + 1] & 0xFF) << 8;
            if (i + 2 < data.length) n |= data[i + 2] & 0xFF;
            out.append(BASE64URL[n >>> 18 & 63]).append(BASE64URL[n >>> 12 & 63]);
            if (i + 1 < data.length) out.append(BASE64URL[n >>> 6 & 63]);
            if (i + 2 < data.length) out.append(BASE64URL[n & 63]);
        }
        return out.toString();
    }

    static String clean(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && out.length() < MAX_VALUE; i++) {
            char c = value.charAt(i);
            if (c == '\n') out.append('\n');
            else if (c >= 0x20 && c < 0x7F) out.append(c);
        }
        return out.toString().trim();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
