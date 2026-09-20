package dev.voidpulsar.lc_claim_economy.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Server-wide aggregate stat counters (shown on the web leaderboard's "Economy Activity"
 * panel) plus the one-time Pioneer Bonus flag - both simple independent counters with no
 * coupling to team-claim state.
 */
final class ServerStatsManager {
    private boolean pioneerClaimGranted = false;

    private long statUpkeepChargedCopper = 0L;
    private int statUpkeepChargedCount = 0;
    private int statUpkeepMissedCount = 0;
    private long statClaimSpendCopper = 0L;
    private int statClaimCount = 0;
    private long statUnclaimRefundCopper = 0L;
    private int statUnclaimCount = 0;
    private long statMarketVolumeCopper = 0L;
    private int statMarketSaleCount = 0;

    private final Runnable markDirty;

    ServerStatsManager(Runnable markDirty) {
        this.markDirty = markDirty;
    }

    void load(CompoundTag tag) {
        pioneerClaimGranted = tag.getBoolean("PioneerClaimGranted");
        statUpkeepChargedCopper = tag.getLong("StatUpkeepChargedCopper");
        statUpkeepChargedCount = tag.getInt("StatUpkeepChargedCount");
        statUpkeepMissedCount = tag.getInt("StatUpkeepMissedCount");
        statClaimSpendCopper = tag.getLong("StatClaimSpendCopper");
        statClaimCount = tag.getInt("StatClaimCount");
        statUnclaimRefundCopper = tag.getLong("StatUnclaimRefundCopper");
        statUnclaimCount = tag.getInt("StatUnclaimCount");
        statMarketVolumeCopper = tag.getLong("StatMarketVolumeCopper");
        statMarketSaleCount = tag.getInt("StatMarketSaleCount");
    }

    void save(CompoundTag tag) {
        tag.putBoolean("PioneerClaimGranted", pioneerClaimGranted);
        tag.putLong("StatUpkeepChargedCopper", statUpkeepChargedCopper);
        tag.putInt("StatUpkeepChargedCount", statUpkeepChargedCount);
        tag.putInt("StatUpkeepMissedCount", statUpkeepMissedCount);
        tag.putLong("StatClaimSpendCopper", statClaimSpendCopper);
        tag.putInt("StatClaimCount", statClaimCount);
        tag.putLong("StatUnclaimRefundCopper", statUnclaimRefundCopper);
        tag.putInt("StatUnclaimCount", statUnclaimCount);
        tag.putLong("StatMarketVolumeCopper", statMarketVolumeCopper);
        tag.putInt("StatMarketSaleCount", statMarketSaleCount);
    }

    /**
     * Marks the server-wide Pioneer Bonus as claimed and returns true, the
     * one time this is ever called successfully - every call after the
     * first (on this server, forever) returns false. Callers use this to
     * gate a one-time reward for whoever claims the very first chunk ever
     * claimed on the server.
     */
    boolean claimPioneerBonus() {
        if (pioneerClaimGranted) {
            return false;
        }
        pioneerClaimGranted = true;
        markDirty.run();
        return true;
    }

    void recordUpkeepCharged(long copper) {
        statUpkeepChargedCopper += copper;
        statUpkeepChargedCount++;
        markDirty.run();
    }

    void recordUpkeepMissed() {
        statUpkeepMissedCount++;
        markDirty.run();
    }

    void recordClaimPurchase(long copper) {
        statClaimSpendCopper += copper;
        statClaimCount++;
        markDirty.run();
    }

    void recordUnclaimRefund(long copper) {
        statUnclaimRefundCopper += copper;
        statUnclaimCount++;
        markDirty.run();
    }

    void recordMarketSale(long copper) {
        statMarketVolumeCopper += copper;
        statMarketSaleCount++;
        markDirty.run();
    }

    long getStatUpkeepChargedCopper() {
        return statUpkeepChargedCopper;
    }

    int getStatUpkeepChargedCount() {
        return statUpkeepChargedCount;
    }

    int getStatUpkeepMissedCount() {
        return statUpkeepMissedCount;
    }

    long getStatClaimSpendCopper() {
        return statClaimSpendCopper;
    }

    int getStatClaimCount() {
        return statClaimCount;
    }

    long getStatUnclaimRefundCopper() {
        return statUnclaimRefundCopper;
    }

    int getStatUnclaimCount() {
        return statUnclaimCount;
    }

    long getStatMarketVolumeCopper() {
        return statMarketVolumeCopper;
    }

    int getStatMarketSaleCount() {
        return statMarketSaleCount;
    }
}
