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

/**
 * FTB fires {@code playerJoinedParty} for both "joined a real party" and "returned to
 * their own solo team," and only the previous-team's type tells them apart -
 * {@code previousTeam.isPartyTeam()} being true means this is a party-to-party move
 * (nothing solo-claim-related to settle), so only a solo-team departure triggers
 * {@link PartyEnrollmentSettlement}.
 */
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
