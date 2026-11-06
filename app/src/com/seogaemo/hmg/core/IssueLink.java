package com.seogaemo.hmg.core;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * GitHub "new issue" link with an issue form prefilled through query parameters, shown as a QR code
 * so a phone can open it. Only fixed, non-personal facts go in (firmware ids, versions, map and
 * module states); values are reduced to printable ASCII and the link stays short enough to scan
 * from the head-unit screen.
 */
public final class IssueLink {
    public static final String NEW_ISSUE = "https://github.com/seogaemo/hmg-seogaemo/issues/new";
    public static final String TEMPLATE_BUG = "bug-car-support.yml";
    public static final String TEMPLATE_MAP = "new-firmware-map.yml";
    static final int MAX_VALUE = 600;

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
