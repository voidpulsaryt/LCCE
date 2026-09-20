package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.ClaimTransferContext;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.service.ClaimPricingBroadcast;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.service.ConflictSyncCoordinator;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyEnrollmentSettlement {
    private static final Set<UUID> SETTLING = ConcurrentHashMap.newKeySet();

    private PartyEnrollmentSettlement() {
    }

    public static void settle(MinecraftServer server, ServerPlayer player, Team previousTeam) {
        if (player == null || previousTeam == null || previousTeam.isPartyTeam()) {
            return;
        }
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }

        UUID playerId = player.getUUID();
        if (!SETTLING.add(playerId)) {
            return;
        }

        try {
            ChunkTeamData personalChunkData = FTBChunksAPI.api().getManager().getOrCreateData(previousTeam);
            if (personalChunkData.getClaimedChunks().isEmpty() && personalChunkData.getForceLoadedChunks().isEmpty()) {
                return;
            }

            LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
            TeamRegistry.dissolveWarLinks(server, previousTeam.getTeamId());
            savedData.setPendingState(previousTeam.getTeamId(), savedData.getPendingState(previousTeam.getTeamId()).cleared());

            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            QueuedChangeService.removeAllForceLoads(server, previousTeam);

            int claimedBefore = personalChunkData.getClaimedChunks().size();
            int[] unclaimed = {0};
            ClaimTransferContext.runPersonalRefundSettlement(playerId, () ->
                    unclaimed[0] = ClaimSettlementSupport.unclaimAll(personalChunkData, source, false)
            );

            if (unclaimed[0] <= 0) {
                return;
            }

            long refundCopper = (long) ComplimentaryChunkAllotment.billableChunkCount(claimedBefore) * ClaimSettlementSupport.refundPerChunk();
            Component refund = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(refundCopper));
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.party_join_claim_refund", refund, unclaimed[0]),
                    false
            );
            ClaimPricingBroadcast.syncToPlayer(player);
            ConflictSyncCoordinator.syncToPlayer(player);

            LcClaimEconomy.LOGGER.info(
                    "Dissolved {} personal claims for {} when joining a party (refund {})",
                    unclaimed[0],
                    playerId,
                    refundCopper
            );
        } catch (Exception exception) {
            LcClaimEconomy.LOGGER.error("Failed to dissolve personal claims for {} on party join", playerId, exception);
        } finally {
            SETTLING.remove(playerId);
        }
    }
}
