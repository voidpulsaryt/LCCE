package dev.voidpulsar.lc_claim_economy.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Everything {@link EmbeddedWebServer}'s handlers need to talk HTTP but shouldn't
 * have to think about inline: draining a request body, pulling a resource out of
 * the mod jar, and writing a response with the right headers. Nothing here knows
 * what a route or a session is - that separation is what lets the handler methods
 * in {@link EmbeddedWebServer} read as pure "decide what to send", not plumbing.
 */
final class WebResponses {
    // Dashboard POST bodies are tiny (a key + a bool, at most a couple of fields) -
    // this just guards against a malformed/hostile client streaming something huge.
    private static final int MAX_BODY_BYTES = 8192;

    private WebResponses() {
    }

    static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream collected = new ByteArrayOutputStream();
            byte[] readBuf = new byte[512];
            int bytesRead;
            int totalRead = 0;
            while ((bytesRead = in.read(readBuf)) != -1) {
                totalRead += bytesRead;
                if (totalRead > MAX_BODY_BYTES) {
                    break;
                }
                collected.write(readBuf, 0, bytesRead);
            }
            return collected.toString(StandardCharsets.UTF_8);
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

    /** Null return means "not found" - callers use that to decide whether to log and skip starting the affected route. */
    static byte[] loadResource(String path) {
        try (InputStream in = WebResponses.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream collected = new ByteArrayOutputStream();
            in.transferTo(collected);
            return collected.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
