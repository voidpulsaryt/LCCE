package dev.voidpulsar.lc_claim_economy.handler;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.bank.BankAccountHelper;
import dev.voidpulsar.lc_claim_economy.bank.ClaimBatchContext;
import dev.voidpulsar.lc_claim_economy.bank.InsufficientFundsClaimResult;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.integration.quest.QuestAdvancements;
import dev.voidpulsar.lc_claim_economy.service.ClaimPriceSync;
import dev.voidpulsar.lc_claim_economy.service.FreeChunkAllowance;
import dev.voidpulsar.lc_claim_economy.util.MoneyMessageUtil;
import dev.voidpulsar.lc_claim_economy.util.MoneyUtil;
import dev.architectury.event.CompoundEventResult;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ChunkClaimHandler {
    public ChunkClaimHandler() {
        ClaimedChunkEvent.BEFORE_CLAIM.register(this::beforeClaim);
        ClaimedChunkEvent.AFTER_CLAIM.register(this::afterClaim);
        ClaimedChunkEvent.AFTER_UNCLAIM.register(this::afterUnclaim);
    }

    private void afterClaim(CommandSourceStack source, ClaimedChunk chunk) {
        int totalClaimed = liveClaimedChunkCount(chunk);
        grantClaimMilestoneAdvancement(source, totalClaimed);
        grantPioneerBonusIfFirstEverClaim(source);

        if (ClaimBatchContext.isExecuting()) {
            int countBeforeClaim = totalClaimed - 1;
            if (FreeChunkAllowance.isClaimFree(countBeforeClaim)) {
                ClaimBatchContext.recordClaimFree();
            }
            return;
        }
        syncClaimUi(source);
    }

    /**
     * A one-time, server-wide bonus (see {@code pioneerBonusAmount} config)
     * for whoever claims the very first chunk ever claimed on this server -
     * gated by {@link LcClaimEconomySavedData#claimPioneerBonus()}, which
     * only returns true once, ever.
     */
    private void grantPioneerBonusIfFirstEverClaim(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return;
        }
        MinecraftServer server = source.getServer();
        if (!LcClaimEconomySavedData.get(server).claimPioneerBonus()) {
            return;
        }

        QuestAdvancements.grant(player, QuestAdvancements.pioneer());

        long bonusCopper = LcClaimEconomyConfig.SERVER.pioneerBonusAmount.get();
        if (bonusCopper <= 0) {
            return;
        }
        MoneyValue bonus = MoneyUtil.fromCopper(bonusCopper);
        IBankAccount account = BankAccountHelper.getAccountForPlayer(server, player);
        account.depositMoney(bonus);
        BankAccountHelper.logTransaction(account, true, bonus, Component.translatable("message.lc_claim_economy.ledger.pioneer_bonus"));

        Component announcement = Component.translatable("message.lc_claim_economy.pioneer_bonus.announcement",
                player.getDisplayName(), MoneyMessageUtil.formatValue(bonus));
        server.getPlayerList().broadcastSystemMessage(announcement, false);
    }

    private void grantClaimMilestoneAdvancement(CommandSourceStack source, int totalClaimed) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return;
        }
        for (int milestone : QuestAdvancements.CLAIM_MILESTONES) {
            if (totalClaimed == milestone) {
                QuestAdvancements.grant(player, QuestAdvancements.claimedChunks(milestone));
                break;
            }
        }
    }

    private CompoundEventResult<ClaimResult> beforeClaim(CommandSourceStack source, ClaimedChunk chunk) {
        if (ClaimBatchContext.isEconomySuppressed()) {
            return CompoundEventResult.pass();
        }
        int currentCount = liveClaimedChunkCount(chunk);
        if (FreeChunkAllowance.isClaimFree(currentCount)) {
            return CompoundEventResult.pass();
        }
        return handlePurchase(source, LcClaimEconomyConfig.SERVER.claimPrice.get());
    }

    private void afterUnclaim(CommandSourceStack source, ClaimedChunk chunk) {
        MinecraftServer unclaimServer = source.getServer();
        if (unclaimServer != null) {
            dev.voidpulsar.lc_claim_economy.service.LandChunkService.onChunkUnclaimed(unclaimServer, chunk);
        }

        long refundAmount = calculateUnclaimRefund(chunk);
        if (refundAmount <= 0) {
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordUnclaim(0);
            } else if (!ClaimBatchContext.suppressNotifications()) {
                syncClaimUi(source);
            }
            return;
        }

        Team team = chunk.getTeamData().getTeam();
        MinecraftServer server = source.getServer();
        if (team == null || server == null) {
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordUnclaim(0);
            } else if (!ClaimBatchContext.suppressNotifications()) {
                syncClaimUi(source);
            }
            return;
        }

        BankAccountHelper.ensurePartyAccountExists(server, team);
        MoneyValue refund = MoneyUtil.fromCopper(refundAmount);
        UUID personalRefundPlayer = ClaimBatchContext.personalRefundPlayerId();
        IBankAccount account;
        if (personalRefundPlayer != null) {
            account = PlayerBankReference.of(personalRefundPlayer).get();
            if (account == null) {
                LcClaimEconomy.LOGGER.warn("Missing personal bank account for refund on party join: {}", personalRefundPlayer);
                if (ClaimBatchContext.isExecuting()) {
                    ClaimBatchContext.recordUnclaim(0);
                }
                return;
            }
        } else {
            account = BankAccountHelper.getAccountForTeam(server, team);
        }
        account.depositMoney(refund);
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        savedData.recordUnclaimRefund(refundAmount);
        if (!ClaimBatchContext.isExecuting()) {
            BankAccountHelper.logTransaction(account, true, refund, Component.translatable("message.lc_claim_economy.ledger.unclaim_refund"));
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordUnclaim(refundAmount);
            } else if (!ClaimBatchContext.suppressNotifications()) {
                int refundPercent = (int) Math.round(LcClaimEconomyConfig.SERVER.unclaimRefundRatio.get() * 100.0D);
                player.displayClientMessage(
                        Component.translatable(
                                "message.lc_claim_economy.unclaim_refund",
                                MoneyMessageUtil.formatValue(refund),
                                refundPercent
                        ),
                        false
                );
                syncClaimUi(source);
            }
            return;
        }
        syncClaimUi(source);
    }

    private long calculateUnclaimRefund(ClaimedChunk chunk) {
        if (ClaimBatchContext.isEconomySuppressed()) {
            return 0L;
        }
        int countBeforeUnclaim = liveClaimedChunkCount(chunk) + 1;
        if (!FreeChunkAllowance.shouldRefundOnUnclaim(countBeforeUnclaim)) {
            return 0L;
        }

        long claimPrice = LcClaimEconomyConfig.SERVER.claimPrice.get();
        double refundRatio = LcClaimEconomyConfig.SERVER.unclaimRefundRatio.get();
        if (claimPrice <= 0 || refundRatio <= 0) {
            return 0L;
        }

        return (long) Math.floor(claimPrice * refundRatio);
    }

    /**
     * {@code ChunkTeamData#getClaimedChunks()} lazily caches its result and
     * is only invalidated by FTB Chunks' own {@code clearClaimCaches()} -
     * which, for unclaim, runs *after* {@code AFTER_UNCLAIM} fires. Reading
     * it from inside an AFTER_CLAIM/AFTER_UNCLAIM listener can therefore
     * return a stale, pre-mutation count (still counting a chunk that was
     * just removed, or missing one that was just added). The manager's
     * {@link FTBChunksAPI#api()}{@code .getManager().getAllClaimedChunks()}
     * has no such cache - {@code registerClaim}/{@code unregisterClaim}
     * mutate its backing map directly - so filter that instead to get the
     * true current count for this chunk's team.
     */
    private static int liveClaimedChunkCount(ClaimedChunk chunk) {
        ChunkTeamData teamData = chunk.getTeamData();
        Team team = teamData.getTeam();
        if (team == null) {
            // Callers that care (afterUnclaim) re-check this and bail out
            // before acting on the count; avoid an NPE here so they can.
            return 0;
        }
        UUID teamId = team.getId();
        int count = 0;
        for (ClaimedChunk other : FTBChunksAPI.api().getManager().getAllClaimedChunks()) {
            Team otherTeam = other.getTeamData().getTeam();
            if (otherTeam != null && otherTeam.getId().equals(teamId)) {
                count++;
            }
        }
        return count;
    }

    private void syncClaimUi(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            ClaimPriceSync.syncToPlayer(player);
        }
    }

    private CompoundEventResult<ClaimResult> handlePurchase(CommandSourceStack source, long priceAmount) {
        if (ClaimBatchContext.isValidating()) {
            return CompoundEventResult.pass();
        }

        ServerPlayer player = source.getPlayer();
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return CompoundEventResult.pass();
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return CompoundEventResult.pass();
        }

        if (!BankAccountHelper.canPurchaseForTeam(team, player.getUUID())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_claim_economy.claim_rank_denied"));
        }

        MoneyValue price = MoneyUtil.fromCopper(priceAmount);
        if (price.isEmpty()) {
            return CompoundEventResult.pass();
        }

        BankAccountHelper.ensurePartyAccountExists(player.server, team);
        IBankAccount account = BankAccountHelper.getAccountForPlayer(player.server, player);

        if (!account.getMoneyStorage().containsValue(price)) {
            Component balance = MoneyMessageUtil.formatBalance(account);
            Component priceText = MoneyMessageUtil.formatValue(price);
            Component message = Component.translatable("message.lc_claim_economy.insufficient_funds", priceText, balance);
            if (ClaimBatchContext.isExecuting()) {
                ClaimBatchContext.recordClaimInsufficientFunds(account, priceAmount);
            } else {
                player.displayClientMessage(message, false);
                ClaimPriceSync.syncToPlayer(player);
            }
            return CompoundEventResult.interruptFalse(new InsufficientFundsClaimResult(message.copy()));
        }

        account.withdrawMoney(price);
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(player.server);
        savedData.recordClaimPurchase(priceAmount);
        if (ClaimBatchContext.isExecuting()) {
            ClaimBatchContext.recordClaimSpend(priceAmount);
        } else {
            BankAccountHelper.logTransaction(account, false, price, Component.translatable("message.lc_claim_economy.ledger.claim_purchase"));
            ServerPlayer payingPlayer = source.getPlayer();
            if (payingPlayer != null) {
                payingPlayer.displayClientMessage(
                        Component.translatable(
                                "message.lc_claim_economy.claim_paid",
                                MoneyMessageUtil.formatValue(price)
                        ),
                        false
                );
                ClaimPriceSync.syncToPlayer(payingPlayer);
            }
        }
        return CompoundEventResult.pass();
    }
}
