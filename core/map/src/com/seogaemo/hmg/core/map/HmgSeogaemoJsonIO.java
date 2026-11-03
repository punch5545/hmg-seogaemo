// Follows HMG-API core/runtime/java/hmgapi/engine/Json.java@4fab437 (JSON interchange only).
package com.seogaemo.hmg.core.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON codec shared by the host and API 19 mapper. */
final class HmgSeogaemoJsonIO {
    private final String input;
    private int offset;
    private HmgSeogaemoJsonIO(String text) { input = text; }
    static Object decode(String text) {
        HmgSeogaemoJsonIO reader = new HmgSeogaemoJsonIO(text);
        Object result = reader.readValue();
        reader.space();
        if (reader.offset != text.length()) throw new IllegalArgumentException("trailing JSON");
        return result;
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> object(String text) { return (Map<String,Object>) decode(text); }
    private void space() { while (offset < input.length() && Character.isWhitespace(input.charAt(offset))) offset++; }
    private char next() { if (offset >= input.length()) throw new IllegalArgumentException("short JSON"); return input.charAt(offset++); }
    private Object readValue() {
        space(); char first = next();
        if (first == '{') {
            Map<String,Object> result = new LinkedHashMap<String,Object>();
            space(); if (input.charAt(offset) == '}') { offset++; return result; }
            for (;;) {
                space(); if (next() != '"') throw new IllegalArgumentException("JSON key");
                String key = quoted(); space(); if (next() != ':') throw new IllegalArgumentException("JSON colon");
                if (result.containsKey(key)) throw new IllegalArgumentException("duplicate JSON key");
                result.put(key, readValue()); space(); char separator = next();
                if (separator == '}') return result;
                if (separator != ',') throw new IllegalArgumentException("JSON object separator");
            }
        }
        if (first == '[') {
            List<Object> result = new ArrayList<Object>();
            space(); if (input.charAt(offset) == ']') { offset++; return result; }
            for (;;) {
                result.add(readValue()); space(); char separator = next();
                if (separator == ']') return result;
                if (separator != ',') throw new IllegalArgumentException("JSON array separator");
            }
        }
        if (first == '"') return quoted();
        offset--;
        if (input.startsWith("true", offset)) { offset += 4; return Boolean.TRUE; }
        if (input.startsWith("false", offset)) { offset += 5; return Boolean.FALSE; }
        if (input.startsWith("null", offset)) { offset += 4; return null; }
        int begin = offset;
        while (offset < input.length() && "-+0123456789.eE".indexOf(input.charAt(offset)) >= 0) offset++;
        if (begin == offset) throw new IllegalArgumentException("JSON value");
        String number = input.substring(begin, offset);
        if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0)
            return Double.valueOf(number);
        return Long.valueOf(number);
    }
    private String quoted() {
        StringBuilder result = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return result.toString();
            if (c != '\\') { result.append(c); continue; }
            char escaped = next();
            if (escaped == 'u') {
                if (offset + 4 > input.length()) throw new IllegalArgumentException("JSON unicode");
                result.append((char) Integer.parseInt(input.substring(offset, offset + 4), 16)); offset += 4;
            } else {
                int position = "\"\\/bfnrt".indexOf(escaped);
                if (position < 0) throw new IllegalArgumentException("JSON escape");
                result.append("\"\\/\b\f\n\r\t".charAt(position));
            }
        }
    }
    static String encode(Object value) { StringBuilder out = new StringBuilder(); append(out, value); return out.toString(); }
    @SuppressWarnings("unchecked")
    private static void append(StringBuilder out, Object value) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof Map) {
            out.append('{'); boolean first = true;
            for (Map.Entry<String,Object> e : ((Map<String,Object>) value).entrySet()) {
                if (!first) out.append(','); first = false;
                append(out,e.getKey()); out.append(':'); append(out,e.getValue());
            }
            out.append('}'); return;
        }
        if (value instanceof List) {
            out.append('['); boolean first = true;
            for (Object item : (List<?>) value) { if (!first) out.append(','); first = false; append(out,item); }
            out.append(']'); return;
        }
        if (value instanceof Number || value instanceof Boolean) { out.append(value); return; }
        out.append('"'); String valueString = String.valueOf(value);
        for (int i=0;i<valueString.length();i++) {
            char c=valueString.charAt(i);
            if (c=='"' || c=='\\') out.append('\\').append(c);
            else if (c=='\n') out.append("\\n");
            else if (c=='\r') out.append("\\r");
            else if (c=='\t') out.append("\\t");
            else if (c<32) { String hex=Integer.toHexString(c); out.append("\\u"); for(int k=hex.length();k<4;k++) out.append('0'); out.append(hex); }
            else out.append(c);
        }
        out.append('"');
    }
}
