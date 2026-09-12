package dev.voidpulsar.lc_claim_economy.service;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure warp-name validation/normalization, split out of {@link WarpService} so it's directly unit-testable. */
public final class WarpNames {
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,32}");

    private WarpNames() {
    }

    /** The trimmed name if it's a valid warp name (letters/digits/underscore, 1-32 chars), otherwise null. */
    @Nullable
    public static String normalize(@Nullable String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        return NAME_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    /** {@link #normalize} lowercased, for use as a map key. */
    @Nullable
    public static String normalizeKey(@Nullable String rawName) {
        String normalized = normalize(rawName);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
