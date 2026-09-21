package dev.voidpulsar.lc_claim_economy.service;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Warp-name rules live here instead of inline in {@link WarpService} so the
 * validation logic can be unit tested without spinning up a server/player.
 */
public final class WarpNames {
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[A-Za-z0-9_]{1,32}");

    private WarpNames() {
    }

    /** Returns the trimmed name when it satisfies the length/character rules, else {@code null}. */
    @Nullable
    public static String normalize(@Nullable String rawName) {
        if (rawName == null) {
            return null;
        }
        String candidate = rawName.trim();
        return ALLOWED_CHARACTERS.matcher(candidate).matches() ? candidate : null;
    }

    /** Lowercases the result of {@link #normalize} so it's safe to use as a case-insensitive map key. */
    @Nullable
    public static String normalizeKey(@Nullable String rawName) {
        String normalized = normalize(rawName);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
