package dev.voidpulsar.lc_claim_economy.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Session-cookie handling for the login-gated dashboard - reading the session cookie
 * off a request and resolving it to a logged-in player via {@link DashboardSessions}.
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
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(SESSION_COOKIE + "=")) {
                return trimmed.substring(SESSION_COOKIE.length() + 1);
            }
        }
        return null;
    }

    static Optional<UUID> resolveSession(HttpExchange exchange) {
        return DashboardSessions.SESSIONS.resolve(sessionToken(exchange));
    }

    /** Checks method + session for a POST action endpoint; sends the error response itself if either fails. */
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
