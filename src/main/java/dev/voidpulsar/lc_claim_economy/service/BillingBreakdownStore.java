package dev.voidpulsar.lc_claim_economy.service;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BillingBreakdownStore {
    private static final Map<UUID, BillingBreakdown> LAST_BY_TEAM = new ConcurrentHashMap<>();

    private BillingBreakdownStore() {
    }

    public static void store(BillingBreakdown breakdown) {
        LAST_BY_TEAM.put(breakdown.teamId(), breakdown);
    }

    @Nullable
    public static BillingBreakdown get(UUID teamId) {
        return LAST_BY_TEAM.get(teamId);
    }
}
