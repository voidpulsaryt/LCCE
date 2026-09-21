package dev.voidpulsar.lc_claim_economy.web.auth;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges an in-game player to the web dashboard without ever needing a
 * password: run {@code /lcce web login} and it stamps out a short code tied
 * to your UUID, type that into the browser and it's exchanged for a session.
 * Nothing here is a secret worth persisting, so codes live and die entirely
 * in memory - a restart wiping them out just means stale codes can't be
 * redeemed later, which is strictly a safety improvement, not a loss.
 */
public final class LoginCodeService {
    // Excludes 0/O/1/I on purpose - a player is reading this off a chat message and
    // typing it back on a different device, so visually ambiguous characters cost
    // real support time.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, LoginCode> activeCodes = new ConcurrentHashMap<>();

    public String issue(UUID playerId, int ttlMinutes) {
        return issue(playerId, ttlMinutes, System.currentTimeMillis());
    }

    String issue(UUID playerId, int ttlMinutes, long nowMillis) {
        pruneExpired(nowMillis);
        String code = mintCode();
        long expiresAt = nowMillis + ttlMinutes * 60_000L;
        activeCodes.put(code, new LoginCode(code, playerId, expiresAt));
        return code;
    }

    /** Single-use: a valid code is consumed on the first successful redemption. */
    public Optional<UUID> redeem(String code) {
        return redeem(code, System.currentTimeMillis());
    }

    Optional<UUID> redeem(String code, long nowMillis) {
        if (code == null) {
            return Optional.empty();
        }
        LoginCode matched = activeCodes.remove(code.trim().toUpperCase(java.util.Locale.ROOT));
        if (matched == null || matched.isExpired(nowMillis)) {
            return Optional.empty();
        }
        return Optional.of(matched.playerId());
    }

    // Runs on every issue() rather than on a timer - this service has no background
    // thread of its own, and login codes are issued often enough (each /lcce web
    // login) that expired entries never accumulate for long between sweeps.
    private void pruneExpired(long nowMillis) {
        activeCodes.values().removeIf(entry -> entry.isExpired(nowMillis));
    }

    private String mintCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
