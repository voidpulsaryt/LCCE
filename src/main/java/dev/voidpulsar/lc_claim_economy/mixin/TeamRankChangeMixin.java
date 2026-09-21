package dev.voidpulsar.lc_claim_economy.mixin;

import dev.voidpulsar.lc_claim_economy.teams.TeamBankLinkRegistry;
import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.common.teams.Team;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When an LC team is linked to an FTB party, that party's own rank structure is the
 * source of truth (mirrored in by {@code CurrencyTeamLinkService}/rank-sync mixins
 * elsewhere) - editing LC ranks directly from the LC side would silently drift out of
 * sync with FTB the next time a sync runs, so promote/demote/owner-change are blocked
 * outright on a linked team rather than allowed to fight the sync.
 */
@Mixin(value = Team.class, remap = false)
public class TeamRankChangeMixin {
    @Inject(method = "changePromoteMember", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$blockManagedPromote(Player actingPlayer, PlayerReference targetMember, CallbackInfo callback) {
        guardManagedRankChange(actingPlayer, callback);
    }

    @Inject(method = "changeDemoteMember", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$blockManagedDemote(Player actingPlayer, PlayerReference targetMember, CallbackInfo callback) {
        guardManagedRankChange(actingPlayer, callback);
    }

    @Inject(method = "changeOwner", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$blockManagedOwnerChange(Player actingPlayer, PlayerReference targetMember, CallbackInfo callback) {
        guardManagedRankChange(actingPlayer, callback);
    }

    /** Shared gate for all three injection points above - only the reflected LC method differs. */
    private void guardManagedRankChange(Player actingPlayer, CallbackInfo callback) {
        long linkedTeamId = ((Team) (Object) this).getID();
        if (!isManagedByLinkedParty(linkedTeamId)) {
            return;
        }
        actingPlayer.displayClientMessage(Component.translatable("message.lc_claim_economy.team_role_change_denied"), true);
        callback.cancel();
    }

    private static boolean isManagedByLinkedParty(long linkedTeamId) {
        MinecraftServer runningServer = ServerLifecycleHooks.getCurrentServer();
        return runningServer != null && TeamBankLinkRegistry.shouldBlockLcTeamRoleChanges(runningServer, linkedTeamId);
    }
}
