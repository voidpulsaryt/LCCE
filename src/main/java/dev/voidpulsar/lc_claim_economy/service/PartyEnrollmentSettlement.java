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

/**
 * Cleans up a player's solo claims the moment they enroll in a party.
 *
 * <p>A personal FTB team's claimed and force-loaded chunks make no sense once the player is
 * folded into a party's territory, so this walks away any leftover claims on the old solo
 * team, refunds the player for what gets auto-released, and pushes fresh client state.
 */
public final class PartyEnrollmentSettlement {

    /** Prevents two join events for the same player from settling the same claims twice. */
    private static final Set<UUID> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private PartyEnrollmentSettlement() {
    }

    public static void settle(MinecraftServer server, ServerPlayer joiningPlayer, Team soloTeam) {
        if (joiningPlayer == null || soloTeam == null || soloTeam.isPartyTeam()) {
            return;
        }
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }

        UUID playerUuid = joiningPlayer.getUUID();
        if (!IN_PROGRESS.add(playerUuid)) {
            return;
        }

        try {
            settleSoloClaims(server, joiningPlayer, soloTeam, playerUuid);
        } catch (Exception exception) {
            LcClaimEconomy.LOGGER.error("Failed to dissolve personal claims for {} on party join", playerUuid, exception);
        } finally {
            IN_PROGRESS.remove(playerUuid);
        }
    }

    private static void settleSoloClaims(MinecraftServer server, ServerPlayer joiningPlayer, Team soloTeam, UUID playerUuid) {
        ChunkTeamData soloChunkData = FTBChunksAPI.api().getManager().getOrCreateData(soloTeam);
        if (soloChunkData.getClaimedChunks().isEmpty() && soloChunkData.getForceLoadedChunks().isEmpty()) {
            // Nothing claimed under the solo team, so there's nothing to refund or clean up.
            return;
        }

        UUID soloTeamId = soloTeam.getTeamId();
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        TeamRegistry.dissolveWarLinks(server, soloTeamId);
        savedData.setPendingState(soloTeamId, savedData.getPendingState(soloTeamId).cleared());

        QueuedChangeService.removeAllForceLoads(server, soloTeam);

        int chunksBeforeRelease = soloChunkData.getClaimedChunks().size();
        int chunksReleased = unclaimUnderRefundBatch(playerUuid, soloChunkData, server);

        if (chunksReleased <= 0) {
            return;
        }

        long copperRefunded = (long) ComplimentaryChunkAllotment.billableChunkCount(chunksBeforeRelease) * ClaimSettlementSupport.refundPerChunk();
        announceRefund(joiningPlayer, chunksReleased, copperRefunded);

        ClaimPricingBroadcast.syncToPlayer(joiningPlayer);
        ConflictSyncCoordinator.syncToPlayer(joiningPlayer);

        LcClaimEconomy.LOGGER.info(
                "Dissolved {} personal claims for {} when joining a party (refund {})",
                chunksReleased,
                playerUuid,
                copperRefunded
        );
    }

    /** Runs the unclaim inside the personal-refund batch context and hands back how many chunks it freed. */
    private static int unclaimUnderRefundBatch(UUID playerUuid, ChunkTeamData soloChunkData, MinecraftServer server) {
        CommandSourceStack silentSource = server.createCommandSourceStack().withSuppressedOutput();
        int[] releasedHolder = {0};
        ClaimTransferContext.runPersonalRefundSettlement(playerUuid, () ->
                releasedHolder[0] = ClaimSettlementSupport.unclaimAll(soloChunkData, silentSource, false)
        );
        return releasedHolder[0];
    }

    private static void announceRefund(ServerPlayer joiningPlayer, int chunksReleased, long copperRefunded) {
        Component refundText = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(copperRefunded));
        joiningPlayer.displayClientMessage(
                Component.translatable("message.lc_claim_economy.party_join_claim_refund", refundText, chunksReleased),
                false
        );
    }
}
