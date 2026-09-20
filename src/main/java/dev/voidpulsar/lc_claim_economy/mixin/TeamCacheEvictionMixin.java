package dev.voidpulsar.lc_claim_economy.mixin;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.teams.CurrencyTeamPurgeGuard;
import dev.voidpulsar.lc_claim_economy.teams.TeamBankLinkRegistry;
import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import io.github.lightman314.lightmanscurrency.api.teams.TeamAPI;
import io.github.lightman314.lightmanscurrency.common.data.types.TeamDataCache;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TeamDataCache.class, remap = false)
public class TeamCacheEvictionMixin {
    @Inject(method = "removeTeam", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$guardManagedTeamRemoval(long id, CallbackInfo ci) {
        if (CurrencyTeamPurgeGuard.isAllowed()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        if (!TeamBankLinkRegistry.shouldBlockLcTeamRemoval(server, id)) {
            return;
        }

        ITeam team = TeamAPI.getApi().GetTeam(false, id);
        if (team != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (team.isOwner(player)) {
                    player.displayClientMessage(Component.translatable("message.lc_claim_economy.team_disband_denied"), true);
                    break;
                }
            }
        }

        LcClaimEconomy.LOGGER.debug("Blocked removal of LC team {} while FTB party link is active", id);
        ci.cancel();
    }

    @Inject(method = "removeTeam", at = @At("TAIL"), remap = false)
    private void lcClaimEconomy$cleanupLinkAfterRemoval(long id, CallbackInfo ci) {
        if (CurrencyTeamPurgeGuard.isAllowed()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        if (TeamBankLinkRegistry.findByLcTeamId(server, id) != null) {
            TeamBankLinkRegistry.unlinkLcTeam(server, id);
        }
    }
}
