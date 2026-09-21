package dev.voidpulsar.lc_claim_economy.web.auth;

import java.util.UUID;

/** One logged-in dashboard session. {@code token} is the exact value stored in the session cookie - see {@link SessionManager}. */
record WebSession(String token, UUID playerId, long expiresAtMillis) {
    boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}
