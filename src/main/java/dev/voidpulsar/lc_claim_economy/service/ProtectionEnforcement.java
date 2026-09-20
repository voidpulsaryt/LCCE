package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsar.lc_claim_economy.data.ChunkCoordKey;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.teams.LandProperties;
import net.minecraft.server.MinecraftServer;

/**
 * The side-effecting half of protection handling: reading/writing live team state, as
 * opposed to {@link SafeguardPricing}'s pure pricing math and property (de)serialization.
 * Split out so {@code SafeguardPricing} is what its name promises - pricing only.
 */
public final class ProtectionEnforcement {
    private ProtectionEnforcement() {
    }

    /**
     * Land chunk count after queued type changes are applied at the next upkeep.
     */
    public static int countEffectiveLandChunks(
            MinecraftServer server,
            Team team,
            ChunkTeamData chunkData,
            TeamQueuedChanges pendingState
    ) {
        java.util.Set<String> landKeys = LcClaimEconomySavedData.get(server).getLandChunks(team.getTeamId());
        int count = 0;
        for (dev.ftb.mods.ftbchunks.api.ClaimedChunk chunk : chunkData.getClaimedChunks()) {
            String key = ChunkCoordKey.encode(chunk.getPos());
            boolean land = landKeys.contains(key);
            if (pendingState.isPendingLandChunk(key)) {
                land = true;
            } else if (pendingState.isPendingBuildChunk(key)) {
                land = false;
            }
            if (land) {
                count++;
            }
        }
        return count;
    }

    public static void applyMinimumProtections(MinecraftServer server, Team team) {
        setAndSync(server, team, FTBChunksProperties.ALLOW_MOB_GRIEFING, true);
        setAndSync(server, team, FTBChunksProperties.ALLOW_EXPLOSIONS, true);
        setAndSync(server, team, FTBChunksProperties.ALLOW_PVP, true);
        setAndSync(server, team, FTBChunksProperties.BLOCK_INTERACT_MODE, PrivacyMode.PUBLIC);
        setAndSync(server, team, FTBChunksProperties.BLOCK_EDIT_MODE, PrivacyMode.PUBLIC);
        setAndSync(server, team, FTBChunksProperties.ENTITY_INTERACT_MODE, PrivacyMode.PUBLIC);
        setAndSync(server, team, FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
        setAndSync(server, team, LandProperties.LAND_BLOCK_INTERACT_MODE, PrivacyMode.PUBLIC);
        setAndSync(server, team, LandProperties.LAND_BLOCK_EDIT_MODE, PrivacyMode.PUBLIC);
    }

    private static <T> void setAndSync(MinecraftServer server, Team team, TeamProperty<T> property, T value) {
        team.setProperty(property, value);
        // Protection properties are not shouldSyncToAll, so syncOnePropertyToAll
        // would do nothing. Push to the team explicitly so member clients see
        // the enforced minimum protections.
        team.syncOnePropertyToTeam(property, value);
    }
}
