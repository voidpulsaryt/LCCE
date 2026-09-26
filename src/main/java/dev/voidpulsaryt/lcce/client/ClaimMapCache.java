package dev.voidpulsaryt.lcce.client;

import dev.ftb.mods.ftbchunks.api.client.event.ChunksUpdatedFromServerEvent;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side record of which team owns which chunk, kept up to date by FTB Chunks' own client
 * sync (the same data its own minimap uses) rather than a separate network channel of our own.
 */
public final class ClaimMapCache {

    private static final Map<ChunkDimPos, UUID> TEAM_BY_CHUNK = new ConcurrentHashMap<>();
    private static final Map<UUID, String> TEAM_NAMES = new ConcurrentHashMap<>();

    private ClaimMapCache() {}

    public static void init() {
        ChunksUpdatedFromServerEvent.UPDATED.register(event -> {
            var teamOpt = event.getTeam();
            if (teamOpt.isEmpty()) {
                event.getChunks().forEach(TEAM_BY_CHUNK::remove);
                return;
            }
            Team team = teamOpt.get();
            TEAM_NAMES.put(team.getId(), team.getName().getString());
            for (ChunkDimPos pos : event.getChunks()) {
                TEAM_BY_CHUNK.put(pos, team.getId());
            }
        });
    }

    public static UUID teamIdAt(ChunkDimPos pos) {
        return TEAM_BY_CHUNK.get(pos);
    }

    public static String teamName(UUID teamId) {
        return TEAM_NAMES.getOrDefault(teamId, teamId.toString());
    }

    public static Map<ChunkDimPos, UUID> snapshot() {
        return Map.copyOf(TEAM_BY_CHUNK);
    }

    /** A stable color per team, derived from its ID so it doesn't drift between sessions. */
    public static int colorFor(UUID teamId) {
        int hash = teamId.hashCode();
        float hue = (hash & 0xFFFF) / 65536f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.65f, 0.95f);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
