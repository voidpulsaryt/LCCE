package dev.voidpulsar.lc_claim_economy.bank;

import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.service.ClaimPricingBroadcast;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

public final class ClaimTransferContext {
    private static final ThreadLocal<Boolean> VALIDATING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SUPPRESS_NOTIFICATIONS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SUPPRESS_ECONOMY = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<UUID> PERSONAL_REFUND_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<BatchState> EXECUTING = new ThreadLocal<>();

    private ClaimTransferContext() {
    }

    public static boolean isValidating() {
        return VALIDATING.get();
    }

    public static void beginValidation() {
        VALIDATING.set(true);
    }

    public static void endValidation() {
        VALIDATING.remove();
    }

    public static boolean isExecuting() {
        return EXECUTING.get() != null;
    }

    public static boolean suppressNotifications() {
        return SUPPRESS_NOTIFICATIONS.get();
    }

    public static void runSuppressingNotifications(Runnable action) {
        SUPPRESS_NOTIFICATIONS.set(true);
        try {
            action.run();
        } finally {
            SUPPRESS_NOTIFICATIONS.remove();
        }
    }

    public static boolean isEconomySuppressed() {
        return SUPPRESS_ECONOMY.get();
    }

    /**
     * Runs a raw claim/unclaim pair (e.g. {@link dev.voidpulsar.lc_claim_economy.service.MarketService}
     * moving a chunk from seller to buyer) without {@link dev.voidpulsar.lc_claim_economy.handler.ChunkAcquisitionHandler}
     * charging the claim price or paying an unclaim refund - the caller
     * already settled payment itself - and without the normal claim/unclaim
     * chat spam, since the caller sends its own messages.
     */
    public static void runAsInternalTransfer(Runnable action) {
        SUPPRESS_NOTIFICATIONS.set(true);
        SUPPRESS_ECONOMY.set(true);
        try {
            action.run();
        } finally {
            SUPPRESS_NOTIFICATIONS.remove();
            SUPPRESS_ECONOMY.remove();
        }
    }

    public static void runPersonalRefundSettlement(UUID playerId, Runnable action) {
        SUPPRESS_NOTIFICATIONS.set(true);
        PERSONAL_REFUND_PLAYER.set(playerId);
        try {
            action.run();
        } finally {
            SUPPRESS_NOTIFICATIONS.remove();
            PERSONAL_REFUND_PLAYER.remove();
        }
    }

    @Nullable
    public static UUID personalRefundPlayerId() {
        return PERSONAL_REFUND_PLAYER.get();
    }

    public static void beginExecution(RequestChunkChangePacket.ChunkChangeOp operation, int chunkCount, UUID playerId) {
        if (chunkCount <= 1) {
            return;
        }
        if (operation != RequestChunkChangePacket.ChunkChangeOp.CLAIM
                && operation != RequestChunkChangePacket.ChunkChangeOp.UNCLAIM) {
            return;
        }
        EXECUTING.set(new BatchState(operation, playerId));
    }

    public static void recordClaimSpend(long priceCopper) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.claimPaidCopper += priceCopper;
        state.claimPaidCount++;
        state.uiSyncNeeded = true;
    }

    public static void recordClaimFree() {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.claimFreeCount++;
        state.uiSyncNeeded = true;
    }

    public static void recordUnclaim(long refundCopper) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.unclaimCount++;
        state.refundCopper += refundCopper;
        state.uiSyncNeeded = true;
    }

    public static void recordClaimInsufficientFunds(IBankAccount account, long unitPriceCopper) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }
        state.claimInsufficientCount++;
        state.claimUnitPriceCopper = unitPriceCopper;
        state.insufficientBalance = CurrencyTextFormat.formatBalance(account);
        state.uiSyncNeeded = true;
    }

    public static void markUiSyncNeeded() {
        BatchState state = EXECUTING.get();
        if (state != null) {
            state.uiSyncNeeded = true;
        }
    }

    public static void flush(@Nullable ServerPlayer player) {
        BatchState state = EXECUTING.get();
        if (state == null) {
            return;
        }

        try {
            if (player == null || !player.getUUID().equals(state.playerId)) {
                return;
            }

            if (state.operation == RequestChunkChangePacket.ChunkChangeOp.UNCLAIM && state.unclaimCount > 0) {
                announceUnclaimBatch(player, state);
                if (state.refundCopper > 0) {
                    BankLedgerAccess.logTransaction(
                            BankLedgerAccess.getAccountForPlayer(player.server, player),
                            true,
                            CurrencyAmounts.fromCopper(state.refundCopper),
                            Component.translatable(state.unclaimCount == 1 ? "message.lc_claim_economy.ledger.unclaim_refund" : "message.lc_claim_economy.ledger.unclaim_refund_bulk")
                    );
                }
            }

            if (state.operation == RequestChunkChangePacket.ChunkChangeOp.CLAIM) {
                announceClaimBatch(player, state);
                if (state.claimPaidCopper > 0) {
                    BankLedgerAccess.logTransaction(
                            BankLedgerAccess.getAccountForPlayer(player.server, player),
                            false,
                            CurrencyAmounts.fromCopper(state.claimPaidCopper),
                            Component.translatable(state.claimPaidCount == 1 ? "message.lc_claim_economy.ledger.claim_purchase" : "message.lc_claim_economy.ledger.claim_purchase_bulk")
                    );
                }
            }

            if (state.uiSyncNeeded) {
                ClaimPricingBroadcast.syncToPlayer(player);
            }
        } finally {
            EXECUTING.remove();
        }
    }

    /** One chat line summarizing every unclaim in the batch: refund amount (if any) and how many chunks it covered. */
    private static void announceUnclaimBatch(ServerPlayer player, BatchState state) {
        if (state.refundCopper <= 0) {
            String key = state.unclaimCount == 1 ? "message.lc_claim_economy.unclaim_bulk_single" : "message.lc_claim_economy.unclaim_bulk";
            tell(player, state.unclaimCount == 1 ? Component.translatable(key) : Component.translatable(key, state.unclaimCount));
            return;
        }

        Component refundText = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(state.refundCopper));
        int refundPercent = unclaimRefundPercent();
        Component message = state.unclaimCount == 1
                ? Component.translatable("message.lc_claim_economy.unclaim_refund", refundText, refundPercent)
                : Component.translatable("message.lc_claim_economy.unclaim_refund_bulk", refundText, state.unclaimCount, refundPercent);
        tell(player, message);
    }

    /** One or two chat lines summarizing every claim attempt in the batch: any insufficient-funds failures, then the successes (paid or free). */
    private static void announceClaimBatch(ServerPlayer player, BatchState state) {
        if (state.claimInsufficientCount > 0) {
            announceInsufficientFunds(player, state);
        }

        int succeededCount = state.claimPaidCount + state.claimFreeCount;
        if (succeededCount <= 0) {
            return;
        }

        if (state.claimPaidCopper <= 0) {
            String key = succeededCount == 1 ? "message.lc_claim_economy.claim_free" : "message.lc_claim_economy.claim_free_bulk";
            tell(player, succeededCount == 1 ? Component.translatable(key) : Component.translatable(key, succeededCount));
            return;
        }

        Component spentText = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(state.claimPaidCopper));
        Component message = succeededCount == 1
                ? Component.translatable("message.lc_claim_economy.claim_paid", spentText)
                : Component.translatable("message.lc_claim_economy.claim_paid_bulk", spentText, succeededCount);
        tell(player, message);
    }

    private static void announceInsufficientFunds(ServerPlayer player, BatchState state) {
        Component unitPriceText = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(state.claimUnitPriceCopper));
        Component balanceText = state.insufficientBalance == null
                ? Component.translatable("message.lc_claim_economy.balance_empty")
                : state.insufficientBalance;
        Component message = state.claimInsufficientCount == 1
                ? Component.translatable("message.lc_claim_economy.insufficient_funds", unitPriceText, balanceText)
                : Component.translatable("message.lc_claim_economy.insufficient_funds_bulk_claim", unitPriceText, state.claimInsufficientCount, balanceText);
        tell(player, message);
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }

    private static int unclaimRefundPercent() {
        return (int) Math.round(LcClaimEconomyConfig.SERVER.unclaimRefundRatio.get() * 100.0D);
    }

    private static final class BatchState {
        private final RequestChunkChangePacket.ChunkChangeOp operation;
        private final UUID playerId;
        private long refundCopper;
        private int unclaimCount;
        private long claimPaidCopper;
        private int claimPaidCount;
        private int claimFreeCount;
        private int claimInsufficientCount;
        private long claimUnitPriceCopper;
        @Nullable
        private Component insufficientBalance;
        private boolean uiSyncNeeded;

        private BatchState(RequestChunkChangePacket.ChunkChangeOp operation, UUID playerId) {
            this.operation = operation;
            this.playerId = playerId;
        }
    }
}
