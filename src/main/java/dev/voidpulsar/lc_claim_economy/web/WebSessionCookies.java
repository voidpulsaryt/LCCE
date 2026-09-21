package dev.voidpulsar.lc_claim_economy.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns the raw {@code Cookie} header into a resolved player id for the
 * dashboard's login-gated routes. {@link EmbeddedWebServer}'s handlers never
 * touch the cookie string themselves - they only ever see an
 * {@code Optional<UUID>}, so a change to the cookie name or format is
 * isolated to this one file.
 */
final class WebSessionCookies {
    static final String SESSION_COOKIE = "lce_session";

    private WebSessionCookies() {
    }

    static String sessionToken(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return null;
        }
        for (String rawCookie : cookieHeader.split(";")) {
            String cookie = rawCookie.trim();
            if (cookie.startsWith(SESSION_COOKIE + "=")) {
                return cookie.substring(SESSION_COOKIE.length() + 1);
            }
        }
        return null;
    }

    static Optional<UUID> resolveSession(HttpExchange exchange) {
        return DashboardSessions.SESSIONS.resolve(sessionToken(exchange));
    }

    /**
     * Combines the method check and the session lookup that every mutating dashboard
     * endpoint needs up front - written this way so each handler in {@link EmbeddedWebServer}
     * can bail out with one line instead of repeating both checks (and their error responses).
     */
    static Optional<UUID> requirePostSession(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return Optional.empty();
        }
        Optional<UUID> playerId = resolveSession(exchange);
        if (playerId.isEmpty()) {
            WebResponses.sendJson(exchange, 401, WebResponses.resultJson(false, "Not logged in."));
        }
        return playerId;
    }
}
