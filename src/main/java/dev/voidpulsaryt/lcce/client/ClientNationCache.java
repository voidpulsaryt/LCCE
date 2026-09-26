package dev.voidpulsaryt.lcce.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last nation name the server has told this client about, per team - populated by
 * {@code NationSyncPayload}. Only ever contains entries for teams the local player is actually a
 * member of, since the server only ever sends this to a team's own online members. An empty
 * string means that team isn't currently in any nation.
 */
public final class ClientNationCache {

    private static final Map<UUID, String> NATIONS = new ConcurrentHashMap<>();

    private ClientNationCache() {}

    public static void update(UUID teamId, String nationName) {
        NATIONS.put(teamId, nationName);
    }

    /** {@code null} means this client hasn't been told anything yet; {@code ""} means "not in a nation". */
    public static String get(UUID teamId) {
        return NATIONS.get(teamId);
    }
}
