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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MassClaimHandler {
    private MassClaimHandler() {
    }

    public static boolean rejectIfInsufficientFunds(
            RequestChunkChangePacket message,
            ServerPlayer player,
            CommandSourceStack source,
            ChunkTeamData chunkTeamData
    ) {
        if (message.action() != RequestChunkChangePacket.ChunkChangeOp.CLAIM || message.chunks().size() <= 1) {
            return false;
        }

        long unitPrice = LcClaimEconomyConfig.SERVER.claimPrice.get();
        if (unitPrice <= 0L) {
            return false;
        }

        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return false;
        }

        if (!BankLedgerAccess.canPurchaseForTeam(team, player.getUUID())) {
            return false;
        }

        ServerLevel level = player.serverLevel();
        int claimableCount = countClaimableChunks(source, chunkTeamData, message.chunks(), level);
        if (claimableCount <= 1) {
            return false;
        }

        int paidClaims = ComplimentaryChunkAllotment.countPaidClaimsInBatch(chunkTeamData.getClaimedChunks().size(), claimableCount);
        if (paidClaims <= 0) {
            return false;
        }

        BankLedgerAccess.ensurePartyAccountExists(player.server, team);
        IBankAccount account = BankLedgerAccess.getAccountForPlayer(player.server, player);
        MoneyValue totalCost = CurrencyAmounts.fromCopper(unitPrice * paidClaims);
        if (account.getMoneyStorage().containsValue(totalCost)) {
            return false;
        }

        Component balance = CurrencyTextFormat.formatBalance(account);
        Component priceText = CurrencyTextFormat.formatValue(totalCost);
        Component chatMessage = Component.translatable(
                MassClaimShortfallResult.RESULT_ID,
                priceText,
                paidClaims,
                balance
        );
        player.displayClientMessage(chatMessage, false);
        ClaimPricingBroadcast.syncToPlayer(player);

        Map<String, Integer> problems = new HashMap<>();
        problems.put(MassClaimShortfallResult.RESULT_ID, paidClaims);
        // ChunkChangeResponsePacket is registered through Architectury's networking layer by
        // FTBChunks, not NeoForge's native one. Sending it via NeoForge's PacketDistributor
        // skips Architectury's payload wrapping and crashes the encoder with a ClassCastException
        // (ChunkChangeResponsePacket -> NetworkAggregator$BufCustomPacketPayload). It must be sent
        // through Architectury's NetworkManager instead, matching how FTBChunks itself sends it.
        NetworkManager.sendToPlayer(
                player,
                new ChunkChangeResponsePacket(message.chunks().size(), 0, problems)
        );
        return true;
    }

    public static ChunkTeamData resolveTeamData(RequestChunkChangePacket message, ServerPlayer player) {
        ChunkTeamData chunkTeamData = null;
        if (message.teamId().isPresent()) {
            Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamByID(message.teamId().get());
            if (team.isEmpty()) {
                return null;
            }
            chunkTeamData = ClaimedChunkManagerImpl.getInstance().getOrCreateData(team.get());
        }
        if (chunkTeamData == null) {
            chunkTeamData = ClaimedChunkManagerImpl.getInstance().getOrCreateData(player);
        }
        return chunkTeamData;
    }

    private static int countClaimableChunks(
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
