package dev.voidpulsar.lc_claim_economy.bank;

import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.service.ClaimPriceSync;
import dev.voidpulsar.lc_claim_economy.util.MoneyMessageUtil;
import dev.voidpulsar.lc_claim_economy.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

public final class ClaimBatchContext {
    private static final ThreadLocal<Boolean> VALIDATING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SUPPRESS_NOTIFICATIONS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SUPPRESS_ECONOMY = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<UUID> PERSONAL_REFUND_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<BatchState> EXECUTING = new ThreadLocal<>();

    private ClaimBatchContext() {
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
     * moving a chunk from seller to buyer) without {@link dev.voidpulsar.lc_claim_economy.handler.ChunkClaimHandler}
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
        state.insufficientBalance = MoneyMessageUtil.formatBalance(account);
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
                sendUnclaimSummary(player, state);
                if (state.refundCopper > 0) {
                    BankAccountHelper.logTransaction(
                            BankAccountHelper.getAccountForPlayer(player.server, player),
                            true,
                            MoneyUtil.fromCopper(state.refundCopper),
                            Component.translatable(state.unclaimCount == 1 ? "message.lc_claim_economy.ledger.unclaim_refund" : "message.lc_claim_economy.ledger.unclaim_refund_bulk")
                    );
                }
            }

            if (state.operation == RequestChunkChangePacket.ChunkChangeOp.CLAIM) {
                sendClaimSummary(player, state);
                if (state.claimPaidCopper > 0) {
                    BankAccountHelper.logTransaction(
                            BankAccountHelper.getAccountForPlayer(player.server, player),
                            false,
                            MoneyUtil.fromCopper(state.claimPaidCopper),
                            Component.translatable(state.claimPaidCount == 1 ? "message.lc_claim_economy.ledger.claim_purchase" : "message.lc_claim_economy.ledger.claim_purchase_bulk")
                    );
                }
            }

            if (state.uiSyncNeeded) {
                ClaimPriceSync.syncToPlayer(player);
            }
        } finally {
            EXECUTING.remove();
        }
    }

    private static void sendUnclaimSummary(ServerPlayer player, BatchState state) {
        int refundPercent = refundPercent();
        if (state.refundCopper > 0) {
            Component refund = MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(state.refundCopper));
            if (state.unclaimCount == 1) {
                player.displayClientMessage(
                        Component.translatable("message.lc_claim_economy.unclaim_refund", refund, refundPercent),
                        false
                );
            } else {
                player.displayClientMessage(
                        Component.translatable(
                                "message.lc_claim_economy.unclaim_refund_bulk",
                                refund,
                                state.unclaimCount,
                                refundPercent
                        ),
                        false
                );
            }
            return;
        }

        if (state.unclaimCount == 1) {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.unclaim_bulk_single"),
                    false
            );
        } else {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.unclaim_bulk", state.unclaimCount),
                    false
            );
        }
    }

    private static void sendClaimSummary(ServerPlayer player, BatchState state) {
        if (state.claimInsufficientCount > 0) {
            Component unitPrice = MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(state.claimUnitPriceCopper));
            Component balance = state.insufficientBalance == null
                    ? Component.translatable("message.lc_claim_economy.balance_empty")
                    : state.insufficientBalance;
            if (state.claimInsufficientCount == 1) {
                player.displayClientMessage(
                        Component.translatable("message.lc_claim_economy.insufficient_funds", unitPrice, balance),
                        false
                );
            } else {
                player.displayClientMessage(
                        Component.translatable(
                                "message.lc_claim_economy.insufficient_funds_bulk_claim",
                                unitPrice,
                                state.claimInsufficientCount,
                                balance
                        ),
                        false
                );
            }
        }

        int claimedCount = state.claimPaidCount + state.claimFreeCount;
        if (claimedCount <= 0) {
            return;
        }

        if (state.claimPaidCopper > 0) {
            Component spent = MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(state.claimPaidCopper));
            if (claimedCount == 1) {
                player.displayClientMessage(
                        Component.translatable("message.lc_claim_economy.claim_paid", spent),
                        false
                );
            } else {
                player.displayClientMessage(
                        Component.translatable("message.lc_claim_economy.claim_paid_bulk", spent, claimedCount),
                        false
                );
            }
            return;
        }

        if (claimedCount == 1) {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.claim_free"),
                    false
            );
        } else {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.claim_free_bulk", claimedCount),
                    false
            );
        }
    }

    private static int refundPercent() {
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
