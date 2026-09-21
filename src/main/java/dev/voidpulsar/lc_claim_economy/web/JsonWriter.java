package dev.voidpulsar.lc_claim_economy.web;

/**
 * Hand-rolled JSON object builder covering only what the dashboard/leaderboard
 * endpoints actually emit: flat objects of primitives/strings, plus nested
 * objects and arrays-of-objects one level deep. Written by hand instead of
 * pulling in Gson because the mod has no JSON library dependency to spare -
 * every response shape here is small and known ahead of time, so a real
 * parser/serializer would be overkill.
 */
final class JsonWriter {
    private final StringBuilder buffer = new StringBuilder();
    private boolean fieldAlreadyWritten = false;

    static JsonWriter object() {
        JsonWriter writer = new JsonWriter();
        writer.buffer.append('{');
        return writer;
    }

    JsonWriter field(String name, String value) {
        separateFromPrevious();
        writeKey(name);
        buffer.append(escapeAndQuote(value));
        return this;
    }

    JsonWriter field(String name, long value) {
        separateFromPrevious();
        writeKey(name);
        buffer.append(value);
        return this;
    }

    JsonWriter field(String name, boolean value) {
        separateFromPrevious();
        writeKey(name);
        buffer.append(value);
        return this;
    }

    JsonWriter field(String name, JsonWriter nested) {
        separateFromPrevious();
        writeKey(name);
        buffer.append(nested.buffer).append('}');
        return this;
    }

    JsonWriter arrayField(String name, java.util.List<JsonWriter> items) {
        separateFromPrevious();
        writeKey(name);
        buffer.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                buffer.append(',');
            }
            JsonWriter item = items.get(i);
            buffer.append(item.buffer).append('}');
        }
        buffer.append(']');
        return this;
    }

    String build() {
        return buffer.append('}').toString();
    }

    // Every field call after the first needs a leading comma; the closing '{' from
    // object() means there's nothing to separate from on the very first call.
    private void separateFromPrevious() {
        if (fieldAlreadyWritten) {
            buffer.append(',');
        }
        fieldAlreadyWritten = true;
    }

    private void writeKey(String name) {
        buffer.append(escapeAndQuote(name)).append(':');
    }

    private static String escapeAndQuote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
