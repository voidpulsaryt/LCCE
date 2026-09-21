package dev.voidpulsar.lc_claim_economy.mixin;

import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.PartyDissolutionSettlement;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Chunks' own {@code deleteTeam} has no pre-deletion hook, and by the time it
 * fires the {@link Team} object may already be partially torn down - so this injects
 * at {@code HEAD}, while the team is still fully valid, to run our own settlement
 * (refund bank transfer for a disbanding party, or just clearing war links for a
 * regular team) before FTB's deletion proceeds. Failures here are caught and logged
 * rather than propagated: a bug in settlement must never block the underlying team
 * deletion the player actually asked for.
 */
@Mixin(value = ClaimedChunkManagerImpl.class, remap = false)
public class ClaimManagerTeamPurgeMixin {
    @Inject(method = "deleteTeam", at = @At("HEAD"), remap = false)
    private void lcClaimEconomy$settleBeforeDelete(Team departingTeam, CallbackInfo callback) {
        MinecraftServer activeServer = ServerLifecycleHooks.getCurrentServer();
        if (departingTeam == null || activeServer == null) {
            return;
        }
        settleQuietly(activeServer, departingTeam);
    }

    /** Failures here are caught and logged rather than propagated - a settlement bug must never block the deletion the player asked for. */
    private static void settleQuietly(MinecraftServer activeServer, Team departingTeam) {
        try {
            if (TeamRegistry.isPartyTeam(departingTeam)) {
                PartyDissolutionSettlement.settle(activeServer, departingTeam);
            } else {
                TeamRegistry.dissolveWarLinks(activeServer, departingTeam.getId());
            }
        } catch (Throwable settlementFailure) {
            LcClaimEconomy.LOGGER.error(
                    "Party disband settlement failed for {} - FTB party deletion will continue",
                    departingTeam.getId(),
                    settlementFailure
            );
        }
    }
}
