package dev.voidpulsar.lc_claim_economy.mixin;

import dev.ftb.mods.ftbchunks.FTBChunks;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.PlayerJoinedPartyTeamEvent;
import dev.voidpulsar.lc_claim_economy.service.PartyEnrollmentSettlement;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FTBChunks.class, remap = false)
public class PartyMembershipJoinMixin {
    @Inject(method = "playerJoinedParty", at = @At("HEAD"), remap = false)
    private void lcClaimEconomy$dissolvePersonalClaims(PlayerJoinedPartyTeamEvent event, CallbackInfo ci) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            return;
        }

        Team previousTeam = event.getPreviousTeam();
        if (previousTeam == null || previousTeam.isPartyTeam()) {
            return;
        }

        PartyEnrollmentSettlement.settle(player.server, player, previousTeam);
    }
}
