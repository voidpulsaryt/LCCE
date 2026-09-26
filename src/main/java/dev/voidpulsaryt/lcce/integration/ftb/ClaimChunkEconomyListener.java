package dev.voidpulsaryt.lcce.integration.ftb;

import dev.architectury.event.CompoundEventResult;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.economy.ClaimCostCalculator;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Wires the LCCE into FTB Chunks' claim/unclaim lifecycle.
 * <p>
 * Claiming/unclaiming costs the <em>claiming player</em> real Lightman's Currency coins directly
 * (via {@link CurrencyBridge}), not the team's own {@link TeamBalance} ledger - claiming land is a
 * personal action a player pays for out of their own wallet, same as a marketplace purchase or a
 * bounty, rather than a pooled team expense. {@code TeamBalance} is reserved for genuinely shared
 * costs (upkeep, wars, state-owned marketplace listings) that any member can fund. The pricing
 * curve itself still scales off how many chunks the <em>team</em> already owns
 * ({@link ClaimCostCalculator}), since sprawl should get pricier regardless of which member is
 * doing the claiming.
 * <p>
 * FTB Chunks docs are explicit that the BEFORE_* events may fire for a "checkOnly" simulation and
 * must not have side effects, so those are used purely to validate affordability and cancel the
 * claim (never to touch the player's wallet). The actual charge/refund only happens in the AFTER_*
 * events, once the claim/unclaim has genuinely gone through - this also covers claims made through
 * FTB Chunks' own {@code RequestChunkChangePacket} (the World Map's drag-select claim menu), since
 * that calls the exact same {@code ChunkTeamData#claim}/{@code unclaim} methods that fire these
 * events, not some separate path.
 */
public final class ClaimChunkEconomyListener {

    private ClaimChunkEconomyListener() {}

    public static void init() {
        ClaimedChunkEvent.BEFORE_CLAIM.register((sourceStack, claimedChunk) -> {
            Optional<ServerPlayer> player = playerOf(sourceStack);
            Optional<Team> team = teamOf(sourceStack);
            if (player.isEmpty() || team.isEmpty()) {
                return CompoundEventResult.pass();
            }

            int owned = claimedChunkCountOf(team.get());
            BigInteger cost = ClaimCostCalculator.costOf(owned);

            if (!CurrencyBridge.canAfford(player.get(), cost)) {
                sourceStack.sendFailure(Component.translatable("lcce.claim.insufficient_funds_player", CurrencyBridge.formatValue(cost)));
                // FTB Chunks treats a null result object as "no objection" and claims anyway, so a
                // real failing ClaimResult is required for the veto to take effect.
                return CompoundEventResult.<ClaimResult>interruptFalse(ClaimResult.customProblem("lcce.claim.blocked"));
            }

            return CompoundEventResult.pass();
        });

        ClaimedChunkEvent.AFTER_CLAIM.register((sourceStack, claimedChunk) -> {
            Optional<ServerPlayer> playerOpt = playerOf(sourceStack);
            Optional<Team> teamOpt = teamOf(sourceStack);
            if (playerOpt.isEmpty() || teamOpt.isEmpty()) {
                return;
            }
            ServerPlayer player = playerOpt.get();
            Team team = teamOpt.get();

            int ownedIncludingNewChunk = claimedChunkCountOf(team);
            BigInteger cost = ClaimCostCalculator.costOf(ownedIncludingNewChunk - 1);
            if (cost.signum() > 0) {
                // Already confirmed affordable in BEFORE_CLAIM - this can only fail here if the
                // player's wallet changed between the two events, an unavoidable small race any
                // check-then-act design has, not something worth blocking the claim over at this point.
                if (CurrencyBridge.withdrawFromPlayer(player, cost)) {
                    sourceStack.sendSuccess(() -> Component.translatable("lcce.claim.charged_player", CurrencyBridge.formatValue(cost)), false);
                }
            }

            if (ownedIncludingNewChunk == 1) {
                BigInteger bonus = BigInteger.valueOf(LCCEConfig.CLAIM_PIONEER_BONUS.get());
                if (bonus.signum() > 0) {
                    CurrencyBridge.depositToPlayer(player, bonus);
                    sourceStack.sendSuccess(() -> Component.translatable("lcce.claim.pioneer_bonus_player", CurrencyBridge.formatValue(bonus)), false);
                }
            }
        });

        ClaimedChunkEvent.AFTER_UNCLAIM.register((sourceStack, claimedChunk) -> {
            Optional<ServerPlayer> playerOpt = playerOf(sourceStack);
            Optional<Team> teamOpt = teamOf(sourceStack);
            if (playerOpt.isEmpty() || teamOpt.isEmpty()) {
                return;
            }
            ServerPlayer player = playerOpt.get();
            Team team = teamOpt.get();

            int ownedBeforeRemoval = claimedChunkCountOf(team) + 1;
            BigInteger refund = ClaimCostCalculator.refundOf(ownedBeforeRemoval);
            if (refund.signum() > 0) {
                CurrencyBridge.depositToPlayer(player, refund);
                sourceStack.sendSuccess(() -> Component.translatable("lcce.unclaim.refunded_player", CurrencyBridge.formatValue(refund)), false);
            }
        });
    }

    private static Optional<ServerPlayer> playerOf(CommandSourceStack sourceStack) {
        return sourceStack.getEntity() instanceof ServerPlayer player ? Optional.of(player) : Optional.empty();
    }

    private static Optional<Team> teamOf(CommandSourceStack sourceStack) {
        if (!(sourceStack.getEntity() instanceof ServerPlayer player)) {
            return Optional.empty();
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Optional.empty();
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
    }

    private static int claimedChunkCountOf(Team team) {
        return FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size();
    }
}
