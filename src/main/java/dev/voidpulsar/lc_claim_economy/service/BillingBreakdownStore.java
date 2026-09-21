package dev.voidpulsar.lc_claim_economy.service;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps only the most recent upkeep breakdown per team, in memory - this is display
 * state for {@code /lcce upkeep_details}, not persisted data, so it's fine for it to
 * reset on server restart.
 */
public final class BillingBreakdownStore {
    private static final Map<UUID, BillingBreakdown> MOST_RECENT_PER_TEAM = new ConcurrentHashMap<>();

    private BillingBreakdownStore() {
    }

    public static void store(BillingBreakdown breakdown) {
        MOST_RECENT_PER_TEAM.put(breakdown.teamId(), breakdown);
    }

    @Nullable
    public static BillingBreakdown get(UUID teamId) {
        return MOST_RECENT_PER_TEAM.get(teamId);
    }
}
