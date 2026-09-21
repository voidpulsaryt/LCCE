package dev.voidpulsar.lc_claim_economy.mixin;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.voidpulsar.lc_claim_economy.teams.CurrencyTeamLinkService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * A promote/demote can change who's an officer, and this mod's LC-side bank rank
 * mapping (see {@link CurrencyTeamLinkService}) needs to stay in step with FTB's own
 * ranks - there's no rank-changed event to listen for instead, so this re-derives the
 * link straight from {@code promote}/{@code demote} returning. {@code ensureLinked}
 * itself is cheap to call redundantly (it's the same resync path used elsewhere), so
 * no attempt is made to diff old vs. new rank here.
 */
@Mixin(value = PartyTeam.class, remap = false)
public class PartyRankBroadcastMixin {
    @Inject(method = "promote", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$syncLcRolesAfterPromote(
            ServerPlayer player,
            Collection<GameProfile> profiles,
            CallbackInfoReturnable<Integer> cir
    ) {
        syncLinkedLcTeam((Team) (Object) this);
    }

    @Inject(method = "demote", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$syncLcRolesAfterDemote(
            ServerPlayer player,
            Collection<GameProfile> profiles,
            CallbackInfoReturnable<Integer> cir
    ) {
        syncLinkedLcTeam((Team) (Object) this);
    }

    private static void syncLinkedLcTeam(Team team) {
        if (!team.isPartyTeam() || !team.isValid()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            CurrencyTeamLinkService.ensureLinked(server, team);
        }
    }
}
