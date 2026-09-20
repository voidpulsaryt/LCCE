package dev.voidpulsar.lc_claim_economy.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Pure I/O plumbing for {@link EmbeddedWebServer}'s HTTP handlers - reading request
 * bodies, loading mod-resource bytes, and writing plain/resource/JSON responses. No
 * route table, no session handling, no domain logic.
 */
final class WebResponses {
    private static final int MAX_BODY_BYTES = 8192;

    private WebResponses() {
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            int read;
            int total = 0;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    break;
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    static String resultJson(boolean ok, String message) {
        return JsonWriter.object().field("ok", ok).field("message", message == null ? "" : message).build();
    }

    static void sendPlain(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    static void sendResource(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    static byte[] loadResource(String path) {
        try (InputStream in = WebResponses.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
