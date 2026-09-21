package dev.voidpulsar.lc_claim_economy.handler;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.bank.ClaimTransferContext;
import dev.voidpulsar.lc_claim_economy.bank.ClaimShortfallResult;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.integration.quest.QuestAdvancements;
import dev.voidpulsar.lc_claim_economy.service.ClaimPricingBroadcast;
import dev.voidpulsar.lc_claim_economy.service.ComplimentaryChunkAllotment;
import dev.voidpulsar.lc_claim_economy.service.LandChunkService;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import dev.architectury.event.CompoundEventResult;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Charges for claiming a chunk, refunds for unclaiming it, and hands out the pioneer bonus and
 * milestone advancements along the way. Every branch here defers to {@link ClaimTransferContext}
 * first - a bulk claim/unclaim drag or a party-join settlement runs its own batched
 * charge/refund/notify pass around the individual per-chunk events this class listens for, so
 * this class's job during a batch shrinks to bookkeeping (tallying free slots, deferring chat
 * spam) rather than actually moving money or messaging the player itself.
 */
public class ChunkAcquisitionHandler {
    public ChunkAcquisitionHandler() {
        ClaimedChunkEvent.BEFORE_CLAIM.register(this::beforeClaim);
        ClaimedChunkEvent.AFTER_CLAIM.register(this::afterClaim);
        ClaimedChunkEvent.AFTER_UNCLAIM.register(this::afterUnclaim);
    }

    private CompoundEventResult<ClaimResult> beforeClaim(CommandSourceStack source, ClaimedChunk chunk) {
        if (ClaimTransferContext.isEconomySuppressed()) {
            return CompoundEventResult.pass();
        }
        int ownedBeforeThisChunk = currentTeamClaimCount(chunk);
        if (ComplimentaryChunkAllotment.isClaimFree(ownedBeforeThisChunk)) {
            return CompoundEventResult.pass();
        }
        return chargeForClaim(source, LcClaimEconomyConfig.SERVER.claimPrice.get());
    }

    private void afterClaim(CommandSourceStack source, ClaimedChunk chunk) {
        int ownedNow = currentTeamClaimCount(chunk);
        grantMilestoneAdvancementIfReached(source, ownedNow);
        payPioneerBonusIfFirstClaimEver(source);

        if (ClaimTransferContext.isExecuting()) {
            // The batch itself tallies free slots as it goes (see MassClaimHandler) - only
            // report this one chunk's contribution, don't charge or message anyone here.
            int ownedBeforeThisChunk = ownedNow - 1;
            if (ComplimentaryChunkAllotment.isClaimFree(ownedBeforeThisChunk)) {
                ClaimTransferContext.recordClaimFree();
            }
            return;
        }
        refreshClientPricing(source);
    }

    private void afterUnclaim(CommandSourceStack source, ClaimedChunk chunk) {
        MinecraftServer server = source.getServer();
        if (server != null) {
            LandChunkService.onChunkUnclaimed(server, chunk);
        }

        long refundCopper = unclaimRefundFor(chunk);
        Team owningTeam = chunk.getTeamData().getTeam();
        if (refundCopper <= 0 || owningTeam == null || server == null) {
            finishUnclaim(source, 0L);
            return;
        }

        BankLedgerAccess.ensurePartyAccountExists(server, owningTeam);
        MoneyValue refund = CurrencyAmounts.fromCopper(refundCopper);
        IBankAccount destination = resolveRefundDestination(server, owningTeam);
        if (destination == null) {
            if (ClaimTransferContext.isExecuting()) {
                ClaimTransferContext.recordUnclaim(0);
            }
            return;
        }

        destination.depositMoney(refund);
        LcClaimEconomySavedData.get(server).recordUnclaimRefund(refundCopper);
        if (!ClaimTransferContext.isExecuting()) {
            BankLedgerAccess.logTransaction(destination, true, refund, Component.translatable("message.lc_claim_economy.ledger.unclaim_refund"));
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            // Unlike the early-return paths above, recordUnclaim here is only ever reached
            // through a real player action - a null-player unclaim (console/automation) falls
            // through to the unconditional refreshClientPricing below instead, matching the
            // original's asymmetry between "no refund happened" and "a refund happened."
            if (ClaimTransferContext.isExecuting()) {
                ClaimTransferContext.recordUnclaim(refundCopper);
            } else if (!ClaimTransferContext.suppressNotifications()) {
                announceUnclaimRefund(player, refund);
                refreshClientPricing(source);
            }
            return;
        }
        refreshClientPricing(source);
    }

    /** Whichever account this refund should land in: the player's own wallet during a party-join settlement, the team's account otherwise. */
    @Nullable
    private IBankAccount resolveRefundDestination(MinecraftServer server, Team owningTeam) {
        UUID personalRefundTarget = ClaimTransferContext.personalRefundPlayerId();
        if (personalRefundTarget == null) {
            return BankLedgerAccess.getAccountForTeam(server, owningTeam);
        }
        IBankAccount account = PlayerBankReference.of(personalRefundTarget).get();
        if (account == null) {
            LcClaimEconomy.LOGGER.warn("Missing personal bank account for refund on party join: {}", personalRefundTarget);
        }
        return account;
    }

    private void announceUnclaimRefund(ServerPlayer player, MoneyValue refund) {
        int refundPercent = (int) Math.round(LcClaimEconomyConfig.SERVER.unclaimRefundRatio.get() * 100.0D);
        player.displayClientMessage(
                Component.translatable(
                        "message.lc_claim_economy.unclaim_refund",
                        CurrencyTextFormat.formatValue(refund),
                        refundPercent
                ),
                false
        );
    }

    /** Shared tail of every {@code afterUnclaim} exit path: tell the active batch what happened, or refresh the solo unclaimer's client pricing. */
    private void finishUnclaim(CommandSourceStack source, long refundCopper) {
        if (ClaimTransferContext.isExecuting()) {
            ClaimTransferContext.recordUnclaim(refundCopper);
            return;
        }
        if (!ClaimTransferContext.suppressNotifications()) {
            refreshClientPricing(source);
        }
    }

    private long unclaimRefundFor(ClaimedChunk chunk) {
        if (ClaimTransferContext.isEconomySuppressed()) {
            return 0L;
        }
        int ownedIncludingThisChunk = currentTeamClaimCount(chunk) + 1;
        if (!ComplimentaryChunkAllotment.shouldRefundOnUnclaim(ownedIncludingThisChunk)) {
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
     * Every listener here fires on FTB's own {@code ClaimedChunkEvent}, which runs before FTB
     * Chunks invalidates {@code ChunkTeamData}'s claimed-chunk cache for the chunk that just
     * changed hands - so reading {@code getClaimedChunks()} straight from the event would return
     * last tick's count, off by one in whichever direction the event just moved. The claim
     * manager's own {@code getAllClaimedChunks()} isn't cached the same way (claim/unclaim writes
     * straight into its backing map), so counting through that instead always reflects the chunk
     * that just changed hands.
     */
    private static int currentTeamClaimCount(ClaimedChunk chunk) {
        Team owningTeam = chunk.getTeamData().getTeam();
        if (owningTeam == null) {
            // afterUnclaim re-checks for a null team itself before using this count, so
            // returning 0 here rather than throwing keeps this helper callable unconditionally.
            return 0;
        }
        UUID teamId = owningTeam.getId();
        int count = 0;
        for (ClaimedChunk candidate : FTBChunksAPI.api().getManager().getAllClaimedChunks()) {
            Team candidateTeam = candidate.getTeamData().getTeam();
            if (candidateTeam != null && candidateTeam.getId().equals(teamId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * A one-time, server-wide bonus (see {@code pioneerBonusAmount} config) for whoever claims
     * the very first chunk ever claimed on this server - gated by
     * {@link LcClaimEconomySavedData#claimPioneerBonus()}, which only returns true once, ever.
     */
    private void payPioneerBonusIfFirstClaimEver(CommandSourceStack source) {
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
        MoneyValue bonus = CurrencyAmounts.fromCopper(bonusCopper);
        IBankAccount account = BankLedgerAccess.getAccountForPlayer(server, player);
        account.depositMoney(bonus);
        BankLedgerAccess.logTransaction(account, true, bonus, Component.translatable("message.lc_claim_economy.ledger.pioneer_bonus"));

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.lc_claim_economy.pioneer_bonus.announcement",
                        player.getDisplayName(), CurrencyTextFormat.formatValue(bonus)),
                false
        );
    }

    private void grantMilestoneAdvancementIfReached(CommandSourceStack source, int totalClaimed) {
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

    private void refreshClientPricing(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            ClaimPricingBroadcast.syncToPlayer(player);
        }
    }

    private CompoundEventResult<ClaimResult> chargeForClaim(CommandSourceStack source, long priceCopper) {
        if (ClaimTransferContext.isValidating()) {
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

        if (!BankLedgerAccess.canPurchaseForTeam(team, player.getUUID())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_claim_economy.claim_rank_denied"));
        }

        MoneyValue price = CurrencyAmounts.fromCopper(priceCopper);
        if (price.isEmpty()) {
            return CompoundEventResult.pass();
        }

        BankLedgerAccess.ensurePartyAccountExists(player.server, team);
        IBankAccount account = BankLedgerAccess.getAccountForPlayer(player.server, player);

        if (!account.getMoneyStorage().containsValue(price)) {
            return CompoundEventResult.interruptFalse(rejectForInsufficientFunds(player, account, price, priceCopper));
        }

        account.withdrawMoney(price);
        LcClaimEconomySavedData.get(player.server).recordClaimPurchase(priceCopper);
        if (ClaimTransferContext.isExecuting()) {
            ClaimTransferContext.recordClaimSpend(priceCopper);
        } else {
            BankLedgerAccess.logTransaction(account, false, price, Component.translatable("message.lc_claim_economy.ledger.claim_purchase"));
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.claim_paid", CurrencyTextFormat.formatValue(price)),
                    false
            );
            ClaimPricingBroadcast.syncToPlayer(player);
        }
        return CompoundEventResult.pass();
    }

    private ClaimShortfallResult rejectForInsufficientFunds(ServerPlayer player, IBankAccount account, MoneyValue price, long priceCopper) {
        Component message = Component.translatable(
                "message.lc_claim_economy.insufficient_funds",
                CurrencyTextFormat.formatValue(price),
                CurrencyTextFormat.formatBalance(account)
        );
        if (ClaimTransferContext.isExecuting()) {
            ClaimTransferContext.recordClaimInsufficientFunds(account, priceCopper);
        } else {
            player.displayClientMessage(message, false);
            ClaimPricingBroadcast.syncToPlayer(player);
        }
        return new ClaimShortfallResult(message.copy());
    }
}
