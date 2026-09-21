package dev.voidpulsar.lc_claim_economy.handler;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbchunks.net.ChunkChangeResponsePacket;
import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.bank.MassClaimShortfallResult;
import dev.voidpulsar.lc_claim_economy.bank.ClaimTransferContext;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.service.ClaimPricingBroadcast;
import dev.voidpulsar.lc_claim_economy.service.ComplimentaryChunkAllotment;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * FTB Chunks' drag-select claim tool submits an entire batch of chunks in a
 * single {@link RequestChunkChangePacket} rather than one packet per chunk,
 * so the usual per-claim affordability check in {@link ChunkAcquisitionHandler}
 * never gets a chance to run before FTB Chunks has already committed the
 * whole batch. This class is the pre-flight check that runs first: it dry-runs
 * the claim (see {@link #tallyClaimableChunks}) to find out how many chunks
 * would actually succeed, prices only the ones past the free allotment, and
 * rejects the whole batch up front if the team can't cover it - rather than
 * letting some chunks get claimed and others silently fail mid-batch.
 */
public final class MassClaimHandler {
    private MassClaimHandler() {
    }

    public static boolean rejectIfInsufficientFunds(
            RequestChunkChangePacket bulkRequest,
            ServerPlayer requester,
            CommandSourceStack source,
            ChunkTeamData chunkTeamData
    ) {
        // Single-chunk (or non-claim) requests go through the normal
        // per-chunk path in ChunkAcquisitionHandler instead.
        if (!isBulkClaimAttempt(bulkRequest)) {
            return false;
        }

        long unitPrice = LcClaimEconomyConfig.SERVER.claimPrice.get();
        if (unitPrice <= 0L) {
            return false;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(requester).orElse(null);
        if (team == null || !BankLedgerAccess.canPurchaseForTeam(team, requester.getUUID())) {
            return false;
        }

        int claimableCount = tallyClaimableChunks(source, chunkTeamData, bulkRequest.chunks(), requester.serverLevel());
        if (claimableCount <= 1) {
            return false;
        }

        int billableClaims = ComplimentaryChunkAllotment.countPaidClaimsInBatch(chunkTeamData.getClaimedChunks().size(), claimableCount);
        if (billableClaims <= 0) {
            return false;
        }

        BankLedgerAccess.ensurePartyAccountExists(requester.server, team);
        IBankAccount account = BankLedgerAccess.getAccountForPlayer(requester.server, requester);
        MoneyValue totalCost = CurrencyAmounts.fromCopper(unitPrice * billableClaims);
        if (account.getMoneyStorage().containsValue(totalCost)) {
            return false;
        }

        reportShortfall(requester, bulkRequest, account, totalCost, billableClaims);
        return true;
    }

    private static boolean isBulkClaimAttempt(RequestChunkChangePacket bulkRequest) {
        return bulkRequest.action() == RequestChunkChangePacket.ChunkChangeOp.CLAIM && bulkRequest.chunks().size() > 1;
    }

    /** Tells the requester why the batch was rejected, then rewinds the client to pre-drag state. */
    private static void reportShortfall(
            ServerPlayer requester,
            RequestChunkChangePacket bulkRequest,
            IBankAccount account,
            MoneyValue totalCost,
            int billableClaims
    ) {
        Component balance = CurrencyTextFormat.formatBalance(account);
        Component priceText = CurrencyTextFormat.formatValue(totalCost);
        Component chatMessage = Component.translatable(
                MassClaimShortfallResult.RESULT_ID,
                priceText,
                billableClaims,
                balance
        );
        requester.displayClientMessage(chatMessage, false);
        ClaimPricingBroadcast.syncToPlayer(requester);
        sendRejectionAck(requester, bulkRequest.chunks().size(), billableClaims);
    }

    private static void sendRejectionAck(ServerPlayer requester, int totalChunks, int billableClaims) {
        Map<String, Integer> problems = new HashMap<>();
        problems.put(MassClaimShortfallResult.RESULT_ID, billableClaims);
        // ChunkChangeResponsePacket is registered through Architectury's networking layer by
        // FTBChunks, not NeoForge's native one. Sending it via NeoForge's PacketDistributor
        // skips Architectury's payload wrapping and crashes the encoder with a ClassCastException
        // (ChunkChangeResponsePacket -> NetworkAggregator$BufCustomPacketPayload). It must be sent
        // through Architectury's NetworkManager instead, matching how FTBChunks itself sends it.
        NetworkManager.sendToPlayer(
                requester,
                new ChunkChangeResponsePacket(totalChunks, 0, problems)
        );
    }

    @Nullable
    public static ChunkTeamData resolveTeamData(RequestChunkChangePacket bulkRequest, ServerPlayer requester) {
        if (bulkRequest.teamId().isEmpty()) {
            return ClaimedChunkManagerImpl.getInstance().getOrCreateData(requester);
        }
        Optional<Team> targetTeam = FTBTeamsAPI.api().getManager().getTeamByID(bulkRequest.teamId().get());
        if (targetTeam.isEmpty()) {
            return null;
        }
        return ClaimedChunkManagerImpl.getInstance().getOrCreateData(targetTeam.get());
    }

    /**
     * Runs FTB Chunks' own claim validation against every chunk in the batch
     * inside a validation window (see {@link ClaimTransferContext#beginValidation}),
     * which stops {@link ChunkAcquisitionHandler}'s per-chunk purchase logic
     * from firing during the dry run - this only needs to know how many
     * chunks *would* succeed, not actually charge for them yet.
     */
    private static int tallyClaimableChunks(
            CommandSourceStack source,
            ChunkTeamData chunkTeamData,
            Set<XZ> chunks,
            ServerLevel level
    ) {
        ClaimTransferContext.beginValidation();
        try {
            int claimableCount = 0;
            for (XZ pos : chunks) {
                ClaimResult result = chunkTeamData.claim(source, pos.dim(level), true);
                if (result.isSuccess()) {
                    claimableCount++;
                }
            }
            return claimableCount;
        } finally {
            ClaimTransferContext.endValidation();
        }
    }
}
