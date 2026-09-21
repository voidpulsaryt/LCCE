package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Implements the {@code siegeModeEnabled} config toggle: once a team has been at war
 * long enough, its explosion protection stops applying. {@code
 * dev.voidpulsar.lc_claim_economy.mixin.ClaimedChunkProtectionMixin} is the actual
 * interception point and defers to {@link #explosionsBypassed} from its {@code
 * allowExplosions} injection.
 */
public final class SiegeModeService {
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private SiegeModeService() {
    }

    /**
     * Whether a claimed chunk's explosion protection should be ignored right now
     * because siege mode kicked in. This is intentionally an all-or-nothing per-team
     * check rather than scoped to a specific attacker: {@code allowExplosions()} gives
     * us no way to know who/what caused the explosion, and FTB Chunks' explosion flag
     * is itself a single per-team switch, not something that can be conditioned on the
     * other side of the war.
     */
    public static boolean explosionsBypassed(ClaimedChunk chunk) {
        if (!LcClaimEconomyConfig.SERVER.siegeModeEnabled.get() || !ConflictService.isEnabled()) {
            return false;
        }

        Team team = chunk.getTeamData().getTeam();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (team == null || server == null) {
            return false;
        }

        long warStartedAt = LcClaimEconomySavedData.get(server).getWarActiveSince(team.getTeamId());
        if (warStartedAt <= 0L) {
            return false;
        }

        long graceMillis = LcClaimEconomyConfig.SERVER.siegeModeGraceHours.get() * MILLIS_PER_HOUR;
        return System.currentTimeMillis() >= warStartedAt + graceMillis;
    }
}
