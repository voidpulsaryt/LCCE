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
import java.util.EnumSet;
import java.util.UUID;

/**
 * Thread-scoped coordination for chunk claim/unclaim flows: suppressing duplicate
 * chat spam and ledger writes while a batch of chunks is being processed together,
 * and rolling the per-chunk outcomes up into a single summary once the batch ends.
 */
public final class ClaimTransferContext {

    private enum Mode {
        VALIDATING,
        SUPPRESS_NOTIFICATIONS,
        SUPPRESS_ECONOMY
    }

    /** Everything this thread is currently doing lives in one slot instead of several independent ThreadLocals. */
    private static final class ThreadScope {
        private final EnumSet<Mode> modes = EnumSet.noneOf(Mode.class);
        private UUID personalRefundPlayer;
        private Ledger ledger;
    }

    private static final ThreadLocal<ThreadScope> SCOPE = ThreadLocal.withInitial(ThreadScope::new);

    private ClaimTransferContext() {
    }

    public static boolean isValidating() {
        return SCOPE.get().modes.contains(Mode.VALIDATING);
    }

    public static void beginValidation() {
        SCOPE.get().modes.add(Mode.VALIDATING);
    }

    public static void endValidation() {
        SCOPE.get().modes.remove(Mode.VALIDATING);
    }

    public static boolean isExecuting() {
        return SCOPE.get().ledger != null;
    }

    public static boolean suppressNotifications() {
        return SCOPE.get().modes.contains(Mode.SUPPRESS_NOTIFICATIONS);
    }

    public static void runSuppressingNotifications(Runnable action) {
        withMode(Mode.SUPPRESS_NOTIFICATIONS, action);
    }

    public static boolean isEconomySuppressed() {
        return SCOPE.get().modes.contains(Mode.SUPPRESS_ECONOMY);
    }

    /**
     * Runs a raw claim/unclaim pair (e.g. {@link dev.voidpulsar.lc_claim_economy.service.MarketService}
     * moving a chunk from seller to buyer) without {@link dev.voidpulsar.lc_claim_economy.handler.ChunkAcquisitionHandler}
     * charging the claim price or paying an unclaim refund - the caller
     * already settled payment itself - and without the normal claim/unclaim
     * chat spam, since the caller sends its own messages.
     */
    public static void runAsInternalTransfer(Runnable action) {
        ThreadScope scope = SCOPE.get();
        scope.modes.add(Mode.SUPPRESS_NOTIFICATIONS);
        scope.modes.add(Mode.SUPPRESS_ECONOMY);
        try {
            action.run();
        } finally {
            scope.modes.remove(Mode.SUPPRESS_NOTIFICATIONS);
            scope.modes.remove(Mode.SUPPRESS_ECONOMY);
        }
    }

    public static void runPersonalRefundSettlement(UUID playerId, Runnable action) {
        ThreadScope scope = SCOPE.get();
        scope.modes.add(Mode.SUPPRESS_NOTIFICATIONS);
        scope.personalRefundPlayer = playerId;
        try {
            action.run();
        } finally {
            scope.modes.remove(Mode.SUPPRESS_NOTIFICATIONS);
            scope.personalRefundPlayer = null;
        }
    }

    @Nullable
    public static UUID personalRefundPlayerId() {
        return SCOPE.get().personalRefundPlayer;
    }

    private static void withMode(Mode mode, Runnable action) {
        ThreadScope scope = SCOPE.get();
        scope.modes.add(mode);
        try {
            action.run();
        } finally {
            scope.modes.remove(mode);
        }
    }

    public static void beginExecution(RequestChunkChangePacket.ChunkChangeOp operation, int chunkCount, UUID playerId) {
        if (chunkCount <= 1) {
            return;
        }
        boolean claimOrUnclaim = operation == RequestChunkChangePacket.ChunkChangeOp.CLAIM
                || operation == RequestChunkChangePacket.ChunkChangeOp.UNCLAIM;
        if (!claimOrUnclaim) {
            return;
        }
        SCOPE.get().ledger = new Ledger(operation, playerId);
    }

    public static void recordClaimSpend(long priceCopper) {
        Ledger ledger = SCOPE.get().ledger;
        if (ledger == null) {
            return;
        }
        ledger.tally[Tally.CLAIM_PAID_COPPER.ordinal()] += priceCopper;
        ledger.tally[Tally.CLAIM_PAID_COUNT.ordinal()] += 1;
        ledger.dirty = true;
    }

    public static void recordClaimFree() {
        Ledger ledger = SCOPE.get().ledger;
        if (ledger == null) {
            return;
        }
        ledger.tally[Tally.CLAIM_FREE_COUNT.ordinal()] += 1;
        ledger.dirty = true;
    }

    public static void recordUnclaim(long refundCopper) {
        Ledger ledger = SCOPE.get().ledger;
        if (ledger == null) {
            return;
        }
        ledger.tally[Tally.UNCLAIM_COUNT.ordinal()] += 1;
        ledger.tally[Tally.REFUND_COPPER.ordinal()] += refundCopper;
        ledger.dirty = true;
    }

    public static void recordClaimInsufficientFunds(IBankAccount account, long unitPriceCopper) {
        Ledger ledger = SCOPE.get().ledger;
        if (ledger == null) {
            return;
        }
        ledger.tally[Tally.CLAIM_INSUFFICIENT_COUNT.ordinal()] += 1;
        ledger.tally[Tally.CLAIM_UNIT_PRICE_COPPER.ordinal()] = unitPriceCopper;
        ledger.insufficientBalanceText = CurrencyTextFormat.formatBalance(account);
        ledger.dirty = true;
    }

    public static void markUiSyncNeeded() {
        Ledger ledger = SCOPE.get().ledger;
        if (ledger != null) {
            ledger.dirty = true;
        }
    }

    public static void flush(@Nullable ServerPlayer player) {
        ThreadScope scope = SCOPE.get();
        Ledger ledger = scope.ledger;
        if (ledger == null) {
            return;
        }

        try {
            if (player == null || !player.getUUID().equals(ledger.playerId)) {
                return;
            }

            switch (ledger.operation) {
                case UNCLAIM -> settleUnclaims(player, ledger);
                case CLAIM -> settleClaims(player, ledger);
                default -> {
                }
            }

            if (ledger.dirty) {
                ClaimPricingBroadcast.syncToPlayer(player);
            }
        } finally {
            scope.ledger = null;
        }
    }

    private static void settleUnclaims(ServerPlayer player, Ledger ledger) {
        long unclaimCount = ledger.tally[Tally.UNCLAIM_COUNT.ordinal()];
        if (unclaimCount <= 0) {
            return;
        }
        long refundCopper = ledger.tally[Tally.REFUND_COPPER.ordinal()];
        announceUnclaimBatch(player, unclaimCount, refundCopper);
        if (refundCopper > 0) {
            BankLedgerAccess.logTransaction(
                    BankLedgerAccess.getAccountForPlayer(player.server, player),
                    true,
                    CurrencyAmounts.fromCopper(refundCopper),
                    Component.translatable(unclaimCount == 1 ? "message.lc_claim_economy.ledger.unclaim_refund" : "message.lc_claim_economy.ledger.unclaim_refund_bulk")
            );
        }
    }

    private static void settleClaims(ServerPlayer player, Ledger ledger) {
        announceClaimBatch(player, ledger);
        long claimPaidCopper = ledger.tally[Tally.CLAIM_PAID_COPPER.ordinal()];
        if (claimPaidCopper > 0) {
            long claimPaidCount = ledger.tally[Tally.CLAIM_PAID_COUNT.ordinal()];
            BankLedgerAccess.logTransaction(
                    BankLedgerAccess.getAccountForPlayer(player.server, player),
                    false,
                    CurrencyAmounts.fromCopper(claimPaidCopper),
                    Component.translatable(claimPaidCount == 1 ? "message.lc_claim_economy.ledger.claim_purchase" : "message.lc_claim_economy.ledger.claim_purchase_bulk")
            );
        }
    }

    /** One chat line summarizing every unclaim in the batch: refund amount (if any) and how many chunks it covered. */
    private static void announceUnclaimBatch(ServerPlayer player, long unclaimCount, long refundCopper) {
        if (refundCopper <= 0) {
            String key = unclaimCount == 1 ? "message.lc_claim_economy.unclaim_bulk_single" : "message.lc_claim_economy.unclaim_bulk";
            tell(player, unclaimCount == 1 ? Component.translatable(key) : Component.translatable(key, (int) unclaimCount));
            return;
        }

        Component refundText = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(refundCopper));
        int refundPercent = unclaimRefundPercent();
        Component message = unclaimCount == 1
                ? Component.translatable("message.lc_claim_economy.unclaim_refund", refundText, refundPercent)
                : Component.translatable("message.lc_claim_economy.unclaim_refund_bulk", refundText, (int) unclaimCount, refundPercent);
        tell(player, message);
    }

    /** One or two chat lines summarizing every claim attempt in the batch: any insufficient-funds failures, then the successes (paid or free). */
    private static void announceClaimBatch(ServerPlayer player, Ledger ledger) {
        long insufficientCount = ledger.tally[Tally.CLAIM_INSUFFICIENT_COUNT.ordinal()];
        if (insufficientCount > 0) {
            announceInsufficientFunds(player, ledger, insufficientCount);
        }

        long succeededCount = ledger.tally[Tally.CLAIM_PAID_COUNT.ordinal()] + ledger.tally[Tally.CLAIM_FREE_COUNT.ordinal()];
        if (succeededCount <= 0) {
            return;
        }

        long claimPaidCopper = ledger.tally[Tally.CLAIM_PAID_COPPER.ordinal()];
        if (claimPaidCopper <= 0) {
            String key = succeededCount == 1 ? "message.lc_claim_economy.claim_free" : "message.lc_claim_economy.claim_free_bulk";
            tell(player, succeededCount == 1 ? Component.translatable(key) : Component.translatable(key, (int) succeededCount));
            return;
        }

        Component spentText = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(claimPaidCopper));
        Component message = succeededCount == 1
                ? Component.translatable("message.lc_claim_economy.claim_paid", spentText)
                : Component.translatable("message.lc_claim_economy.claim_paid_bulk", spentText, (int) succeededCount);
        tell(player, message);
    }

    private static void announceInsufficientFunds(ServerPlayer player, Ledger ledger, long insufficientCount) {
        long unitPriceCopper = ledger.tally[Tally.CLAIM_UNIT_PRICE_COPPER.ordinal()];
        Component unitPriceText = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(unitPriceCopper));
        Component balanceText = ledger.insufficientBalanceText == null
                ? Component.translatable("message.lc_claim_economy.balance_empty")
                : ledger.insufficientBalanceText;
        Component message = insufficientCount == 1
                ? Component.translatable("message.lc_claim_economy.insufficient_funds", unitPriceText, balanceText)
                : Component.translatable("message.lc_claim_economy.insufficient_funds_bulk_claim", unitPriceText, (int) insufficientCount, balanceText);
        tell(player, message);
    }

    private static void tell(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }

    private static int unclaimRefundPercent() {
        return (int) Math.round(LcClaimEconomyConfig.SERVER.unclaimRefundRatio.get() * 100.0D);
    }

    /** Index space for the running totals kept while a batch is in flight; backed by a flat array rather than named fields. */
    private enum Tally {
        UNCLAIM_COUNT,
        REFUND_COPPER,
        CLAIM_PAID_COPPER,
        CLAIM_PAID_COUNT,
        CLAIM_FREE_COUNT,
        CLAIM_INSUFFICIENT_COUNT,
        CLAIM_UNIT_PRICE_COPPER
    }

    private static final class Ledger {
        private final RequestChunkChangePacket.ChunkChangeOp operation;
        private final UUID playerId;
        private final long[] tally = new long[Tally.values().length];
        @Nullable
        private Component insufficientBalanceText;
        private boolean dirty;

        private Ledger(RequestChunkChangePacket.ChunkChangeOp operation, UUID playerId) {
            this.operation = operation;
            this.playerId = playerId;
        }
    }
}
