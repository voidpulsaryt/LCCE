package dev.voidpulsar.lc_claim_economy.network;

/**
 * Where a conflict-list entry sits relative to the {@code warDeclarationWindow} cooldown:
 * {@code ACTIVE} wars/targets need no window, {@code PENDING_DECLARE} means a war was requested
 * but hasn't cleared the delay yet, and {@code PENDING_END} means an active war's end was
 * requested and is itself waiting out the window before it actually stops.
 */
public enum ConflictEntryStatus {
    ACTIVE,
    PENDING_DECLARE,
    PENDING_END;

    public static ConflictEntryStatus fromId(int encoded) {
        ConflictEntryStatus[] known = values();
        if (encoded < 0 || encoded >= known.length) {
            return ACTIVE;
        }
        return known[encoded];
    }

    public int id() {
        return ordinal();
    }

    public boolean isPending() {
        return this != ACTIVE;
    }
}
