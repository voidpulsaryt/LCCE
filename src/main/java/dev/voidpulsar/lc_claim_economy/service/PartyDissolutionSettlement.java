package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.teams.CurrencyTeamLinkService;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Closes out a party's LC footprint the moment FTB Teams disbands it.
 *
 * <p>When a party goes away its war entries and queued protection changes must not linger
 * in {@link LcClaimEconomySavedData}, and whatever money and claimed land it still held has
 * to land somewhere sane — the former owner's personal account and, for any chunks that get
 * auto-released, a cash refund. None of this depends on the owner still being online.
 */
public final class PartyDissolutionSettlement {

    /** Guards against re-entrant settlement of the same party (disband events can double-fire). */
    private static final Set<UUID> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private PartyDissolutionSettlement() {
    }

    /** Outcome of releasing whatever chunks the disbanding party still had claimed. */
    private record ChunkReleaseOutcome(int chunksHeld, int chunksReleased, long copperRefunded) {
        private static final ChunkReleaseOutcome NONE = new ChunkReleaseOutcome(0, 0, 0L);
    }

    public static void settle(MinecraftServer server, Team party) {
        if (!party.isPartyTeam()) {
            return;
        }

        UUID partyId = party.getId();
        if (!IN_PROGRESS.add(partyId)) {
            return;
        }

        try {
            runSettlement(server, party, partyId);
        } catch (Exception exception) {
            LcClaimEconomy.LOGGER.error("Failed to settle disbanded party {}", partyId, exception);
        } finally {
            IN_PROGRESS.remove(partyId);
        }
    }

    private static void runSettlement(MinecraftServer server, Team party, UUID partyId) {
        // These two steps happen no matter what follows below: a party with an unresolvable
        // owner or missing bank link still must not leave orphaned wars or queued changes behind.
        purgeWarsAndPendingState(server, partyId);

        UUID ownerUuid = party.getOwner();
        if (ownerUuid == null) {
            return;
        }

        IBankAccount partyAccount = CurrencyTeamLinkService.getLinkedBankAccount(server, partyId);
        if (partyAccount == null) {
            LcClaimEconomy.LOGGER.warn("No linked LC bank account found for disbanding party {}", partyId);
            return;
        }

        IBankAccount ownerAccount = PlayerBankReference.of(ownerUuid).get();
        if (ownerAccount == null) {
            LcClaimEconomy.LOGGER.warn(
                    "Could not transfer party funds for {}: missing personal account for owner {}",
                    partyId,
                    ownerUuid
            );
            return;
        }

        Component balanceBeforeRefund = CurrencyTextFormat.formatBalance(partyAccount);
        ChunkReleaseOutcome chunkOutcome = releasePartyChunks(server, party);

        if (partyAccount.getMoneyStorage().isEmpty() && chunkOutcome.chunksReleased() == 0) {
            return;
        }

        Component balanceAfterRefund = CurrencyTextFormat.formatBalance(partyAccount);
        moveEverythingTo(partyAccount, ownerAccount);
        notifyOwner(server, ownerUuid, balanceBeforeRefund, balanceAfterRefund, chunkOutcome.chunksReleased(), chunkOutcome.copperRefunded());

        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUuid);
        if (ownerPlayer != null) {
            ConflictSyncCoordinator.syncToPlayer(ownerPlayer);
        }

        LcClaimEconomy.LOGGER.info(
                "Settled disbanded party {} for owner {} ({} chunks, {} refund copper)",
                partyId,
                ownerUuid,
                chunkOutcome.chunksReleased(),
                chunkOutcome.copperRefunded()
        );
    }

    private static void purgeWarsAndPendingState(MinecraftServer server, UUID partyId) {
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        TeamRegistry.dissolveWarLinks(server, partyId);
        savedData.setPendingState(partyId, savedData.getPendingState(partyId).cleared());
    }

    /** Auto-unclaims every chunk the party still holds and reports how much refund that earns. */
    private static ChunkReleaseOutcome releasePartyChunks(MinecraftServer server, Team party) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return ChunkReleaseOutcome.NONE;
        }

        QueuedChangeService.removeAllForceLoads(server, party);
        ChunkTeamData teamChunkData = FTBChunksAPI.api().getManager().getOrCreateData(party);
        CommandSourceStack silentSource = server.createCommandSourceStack().withSuppressedOutput();

        int chunksHeld = teamChunkData.getClaimedChunks().size();
        int chunksReleased = ClaimSettlementSupport.unclaimAll(teamChunkData, silentSource);
        long copperRefunded = (long) ComplimentaryChunkAllotment.billableChunkCount(chunksHeld) * ClaimSettlementSupport.refundPerChunk();

        return new ChunkReleaseOutcome(chunksHeld, chunksReleased, copperRefunded);
    }

    private static void moveEverythingTo(IBankAccount fromAccount, IBankAccount toAccount) {
        for (MoneyValue heldValue : fromAccount.getMoneyStorage().allValues()) {
            if (heldValue.isEmpty()) {
                continue;
            }
            fromAccount.withdrawMoney(heldValue);
            toAccount.depositMoney(heldValue);
        }
    }

    private static void notifyOwner(
            MinecraftServer server,
            UUID ownerUuid,
            Component balanceBeforeRefund,
            Component balanceAfterRefund,
            int chunksReleased,
            long copperRefunded
    ) {
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUuid);
        if (ownerPlayer == null) {
            return;
        }

        Component refundText = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(copperRefunded));
        ownerPlayer.displayClientMessage(
                Component.translatable(
                        "message.lc_claim_economy.party_disband_settlement",
                        balanceBeforeRefund,
                        balanceAfterRefund,
                        chunksReleased,
                        refundText
                ),
                false
        );
    }
}
