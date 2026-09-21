package dev.voidpulsar.lc_claim_economy.web.auth;

import java.util.UUID;

/** One outstanding, not-yet-redeemed login code. Removed from {@link LoginCodeService}'s map on redemption or expiry - never mutated in place. */
record LoginCode(String code, UUID playerId, long expiresAtMillis) {
    boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}
