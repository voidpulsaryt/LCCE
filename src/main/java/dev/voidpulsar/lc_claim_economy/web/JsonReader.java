package dev.voidpulsar.lc_claim_economy.web;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads exactly the request-body shape the dashboard's POST endpoints ever
 * send: a single flat JSON object, values being strings/booleans/numbers,
 * never nested objects or arrays. That narrow scope is what lets this stay
 * a straight character scan instead of a real parser - no Gson dependency
 * to add just for a handful of one-level bodies (see {@link JsonWriter}).
 */
final class JsonReader {
    private final Map<String, String> values = new HashMap<>();

    private JsonReader() {
    }

    static JsonReader parse(String body) {
        JsonReader reader = new JsonReader();
        if (body == null) {
            return reader;
        }
        int pos = advancePastWhitespace(body, 0);
        if (pos >= body.length() || body.charAt(pos) != '{') {
            return reader;
        }
        pos++;
        while (pos < body.length()) {
            pos = advancePastWhitespace(body, pos);
            if (pos >= body.length() || body.charAt(pos) == '}') {
                break;
            }
            if (body.charAt(pos) != '"') {
                break;
            }
            int[] afterKey = new int[1];
            String key = readQuotedString(body, pos, afterKey);
            pos = advancePastWhitespace(body, afterKey[0]);
            if (pos >= body.length() || body.charAt(pos) != ':') {
                break;
            }
            pos = advancePastWhitespace(body, pos + 1);
            if (pos >= body.length()) {
                break;
            }
            String value;
            int[] afterValue = new int[1];
            char firstChar = body.charAt(pos);
            if (firstChar == '"') {
                value = readQuotedString(body, pos, afterValue);
                pos = afterValue[0];
            } else {
                int start = pos;
                while (pos < body.length() && body.charAt(pos) != ',' && body.charAt(pos) != '}') {
                    pos++;
                }
                value = body.substring(start, pos).trim();
            }
            reader.values.put(key, value);
            pos = advancePastWhitespace(body, pos);
            if (pos < body.length() && body.charAt(pos) == ',') {
                pos++;
            }
        }
        return reader;
    }

    // endOut carries the index just past the closing quote back to the caller, since Java
    // has no multi-return - avoids parsing the same string twice to recover cursor position.
    private static String readQuotedString(String body, int quoteStart, int[] endOut) {
        StringBuilder out = new StringBuilder();
        int pos = quoteStart + 1;
        while (pos < body.length() && body.charAt(pos) != '"') {
            char c = body.charAt(pos);
            if (c == '\\' && pos + 1 < body.length()) {
                char escaped = body.charAt(pos + 1);
                switch (escaped) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(escaped);
                }
                pos += 2;
            } else {
                out.append(c);
                pos++;
            }
        }
        endOut[0] = pos + 1;
        return out.toString();
    }

    private static int advancePastWhitespace(String body, int pos) {
        while (pos < body.length() && Character.isWhitespace(body.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    String getString(String key) {
        return values.get(key);
    }

    boolean getBoolean(String key, boolean fallback) {
        String raw = values.get(key);
        if (raw == null) {
            return fallback;
        }
        return Boolean.parseBoolean(raw);
    }
}
