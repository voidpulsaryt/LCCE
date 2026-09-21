package dev.voidpulsar.lc_claim_economy.web.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which bearer token (the value stored in the dashboard's
 * {@code HttpOnly} cookie) currently belongs to which player. Intentionally
 * in-memory only - persisting tokens to disk would mean a leaked save file
 * doubles as a leaked login, and forcing everyone to log back in after a
 * restart is a small price for not having that risk at all.
 */
public final class SessionManager {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, WebSession> sessionsByToken = new ConcurrentHashMap<>();

    public String create(UUID playerId, int ttlMinutes) {
        return create(playerId, ttlMinutes, System.currentTimeMillis());
    }

    String create(UUID playerId, int ttlMinutes, long nowMillis) {
        String token = mintToken();
        long expiresAt = nowMillis + ttlMinutes * 60_000L;
        sessionsByToken.put(token, new WebSession(token, playerId, expiresAt));
        return token;
    }

    public Optional<UUID> resolve(String token) {
        return resolve(token, System.currentTimeMillis());
    }

    Optional<UUID> resolve(String token, long nowMillis) {
        if (token == null) {
            return Optional.empty();
        }
        WebSession session = sessionsByToken.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired(nowMillis)) {
            sessionsByToken.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.playerId());
    }

    public void invalidate(String token) {
        if (token != null) {
            sessionsByToken.remove(token);
        }
    }

    // URL-safe + unpadded so the value drops straight into a Set-Cookie header without
    // needing any further encoding, and 32 random bytes leaves guessing infeasible.
    private String mintToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        random.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
