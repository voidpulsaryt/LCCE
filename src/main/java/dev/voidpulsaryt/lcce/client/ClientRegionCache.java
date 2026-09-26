package dev.voidpulsaryt.lcce.client;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.voidpulsaryt.lcce.network.RegionSyncPayload;
import dev.voidpulsaryt.lcce.network.RegionSyncPayload.RegionEntry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last set of regions the server has told this client about, per team - populated by
 * {@link RegionSyncPayload}. Only ever contains a team's regions if the local player is actually
 * a member of it, since the server only ever sends this to a team's own online members.
 * <p>
 * Each incoming payload is that team's <em>entire</em> current set of regions, not a diff, so this
 * simply replaces whatever it previously knew about that one team wholesale ({@code byTeam}) and
 * rebuilds a flat per-chunk index ({@code byChunk}) from every team's latest snapshot - correct
 * even for a region being deleted or a chunk removed from one, and cheap since a player is only
 * ever a member of one team at a time in practice.
 */
public final class ClientRegionCache {

    private static final Map<UUID, RegionSyncPayload> BY_TEAM = new ConcurrentHashMap<>();
    private static final Map<ChunkDimPos, RegionEntry> BY_CHUNK = new ConcurrentHashMap<>();

    private ClientRegionCache() {}

    public static synchronized void update(RegionSyncPayload payload) {
        BY_TEAM.put(payload.teamId(), payload);
        BY_CHUNK.clear();
        for (RegionSyncPayload teamPayload : BY_TEAM.values()) {
            for (RegionEntry region : teamPayload.regions()) {
                for (ChunkDimPos pos : region.chunks()) {
                    BY_CHUNK.put(pos, region);
                }
            }
        }
    }

    /** {@code null} means this chunk isn't part of any region this client knows about. */
    public static RegionEntry regionAt(ChunkDimPos pos) {
        return BY_CHUNK.get(pos);
    }
}
