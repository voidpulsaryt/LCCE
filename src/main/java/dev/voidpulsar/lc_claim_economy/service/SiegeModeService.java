package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Backs the {@code siegeModeEnabled} config option. The actual interception
 * point is {@code dev.voidpulsar.lc_claim_economy.mixin.ClaimedChunkProtectionMixin},
 * which calls into this class from its {@code allowExplosions} injection.
 */
public final class SiegeModeService {
    private SiegeModeService() {
    }

    /**
     * True if this chunk's explosion protection should be bypassed because
     * its team has been at war for longer than the configured grace period
     * and siege mode is on. Deliberately blanket per-team (not scoped to the
     * specific war opponent) - {@code allowExplosions()} carries no
     * information about what caused the explosion to check against, and FTB
     * Chunks' own explosion protection is a per-team setting to begin with,
     * not per-attacker.
     */
    public static boolean explosionsBypassed(ClaimedChunk chunk) {
        if (!LcClaimEconomyConfig.SERVER.siegeModeEnabled.get() || !ConflictService.isEnabled()) {
            return false;
        }
        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return false;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        long activeSince = LcClaimEconomySavedData.get(server).getWarActiveSince(team.getTeamId());
        if (activeSince <= 0L) {
            return false;
        }
        long graceMillis = LcClaimEconomyConfig.SERVER.siegeModeGraceHours.get() * 3_600_000L;
        return System.currentTimeMillis() >= activeSince + graceMillis;
    }
}
