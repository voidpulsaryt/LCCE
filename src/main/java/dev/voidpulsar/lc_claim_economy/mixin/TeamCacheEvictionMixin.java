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

/**
 * LC lets a player disband their own LC-side team independently of the FTB party it's
 * linked to, which would leave {@link TeamBankLinkRegistry}'s link pointing at a
 * deleted account. This mixin blocks that removal while the link is still active
 * (unless it's this mod's own cleanup code doing the removing, signaled by
 * {@link CurrencyTeamPurgeGuard}) and unregisters the link once a removal is allowed
 * to go through, so the two team systems can't drift out of sync with each other.
 */
@Mixin(value = TeamDataCache.class, remap = false)
public class TeamCacheEvictionMixin {
    @Inject(method = "removeTeam", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$guardManagedTeamRemoval(long lcTeamId, CallbackInfo callback) {
        if (CurrencyTeamPurgeGuard.isAllowed()) {
            return;
        }

        MinecraftServer activeServer = ServerLifecycleHooks.getCurrentServer();
        if (activeServer == null) {
            return;
        }

        if (!TeamBankLinkRegistry.shouldBlockLcTeamRemoval(activeServer, lcTeamId)) {
            return;
        }

        ITeam blockedTeam = TeamAPI.getApi().GetTeam(false, lcTeamId);
        if (blockedTeam != null) {
            for (ServerPlayer onlinePlayer : activeServer.getPlayerList().getPlayers()) {
                if (blockedTeam.isOwner(onlinePlayer)) {
                    onlinePlayer.displayClientMessage(Component.translatable("message.lc_claim_economy.team_disband_denied"), true);
                    break;
                }
            }
        }

        LcClaimEconomy.LOGGER.debug("Blocked removal of LC team {} while FTB party link is active", lcTeamId);
        callback.cancel();
    }

    @Inject(method = "removeTeam", at = @At("TAIL"), remap = false)
    private void lcClaimEconomy$cleanupLinkAfterRemoval(long lcTeamId, CallbackInfo callback) {
        if (CurrencyTeamPurgeGuard.isAllowed()) {
            return;
        }

        MinecraftServer activeServer = ServerLifecycleHooks.getCurrentServer();
        if (activeServer == null) {
            return;
        }

        if (TeamBankLinkRegistry.findByLcTeamId(activeServer, lcTeamId) != null) {
            TeamBankLinkRegistry.unlinkLcTeam(activeServer, lcTeamId);
        }
    }
}
