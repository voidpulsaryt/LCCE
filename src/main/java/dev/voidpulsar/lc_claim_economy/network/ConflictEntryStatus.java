package dev.voidpulsar.lc_claim_economy.network;

public enum ConflictEntryStatus {
    ACTIVE,
    PENDING_DECLARE,
    PENDING_END;

    public static ConflictEntryStatus fromId(int id) {
        ConflictEntryStatus[] values = values();
        if (id < 0 || id >= values.length) {
            return ACTIVE;
        }
        return values[id];
    }

    public int id() {
        return ordinal();
    }

    public boolean isPending() {
        return this != ACTIVE;
    }
}
