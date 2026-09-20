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

@Mixin(value = ClaimedChunkManagerImpl.class, remap = false)
public class ClaimManagerTeamPurgeMixin {
    @Inject(method = "deleteTeam", at = @At("HEAD"), remap = false)
    private void lcClaimEconomy$settleBeforeDelete(Team team, CallbackInfo ci) {
        if (team == null) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        try {
            if (TeamRegistry.isPartyTeam(team)) {
                PartyDissolutionSettlement.settle(server, team);
            } else {
                TeamRegistry.dissolveWarLinks(server, team.getId());
            }
        } catch (Throwable error) {
            LcClaimEconomy.LOGGER.error(
                    "Party disband settlement failed for {} - FTB party deletion will continue",
                    team.getId(),
                    error
            );
        }
    }
}
