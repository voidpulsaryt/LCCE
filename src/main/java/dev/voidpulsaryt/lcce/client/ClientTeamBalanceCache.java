package dev.voidpulsaryt.lcce.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last balance the server has told this client about, per team - populated by
 * {@code TeamBalanceSyncPayload}. Only ever contains entries for teams the local player is
 * actually a member of, since the server only ever sends this to a team's own online members.
 */
public final class ClientTeamBalanceCache {

    private static final Map<UUID, Long> BALANCES = new ConcurrentHashMap<>();

    private ClientTeamBalanceCache() {}

    public static void update(UUID teamId, long balance) {
        BALANCES.put(teamId, balance);
    }

    /** {@code null} means this client hasn't been told this team's balance (most likely: it isn't a member). */
    public static Long get(UUID teamId) {
        return BALANCES.get(teamId);
    }
}
