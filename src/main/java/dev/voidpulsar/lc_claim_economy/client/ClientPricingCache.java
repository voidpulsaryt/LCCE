package dev.voidpulsar.lc_claim_economy.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.voidpulsar.lc_claim_economy.bank.MassClaimShortfallResult;
import dev.voidpulsar.lc_claim_economy.bank.ClaimShortfallResult;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientPricingCache {
    public static final Set<String> LC_CLAIM_RESULT_IDS = Set.of(
            ClaimShortfallResult.RESULT_ID,
            MassClaimShortfallResult.RESULT_ID,
            "message.lc_claim_economy.claim_rank_denied"
    );

    // These four colors intentionally echo FTB Chunks' own bottom-bar text (white
    // labels, vanilla green values) rather than this mod's usual Nord palette,
    // because ChunkScreenCustomBottomPanelMixin paints this segment directly
    // into FTB's existing bottom bar. That bar already supplies its own
    // background, so a mismatched color scheme here would read as a bug, not
    // a feature - matching it exactly makes the injected text disappear into
    // the vanilla panel like it was always part of it.
    private static final Color4I LABEL_COLOR = Color4I.rgb(0xFFFFFF);
    private static final Color4I SEPARATOR_COLOR = Color4I.rgb(0xAAAAAA);
    private static final Color4I VALUE_COLOR = Color4I.rgb(0x55FF55);
    private static final Color4I PERIOD_COLOR = Color4I.rgb(0xAAAAAA);

    private static long claimPrice = -1L;
    private static long forceLoadUpkeepPrice = -1L;
    private static int upkeepPeriodMinutes = -1;
    private static int freeChunks;
    private static int claimedChunks;
    private static long mobGriefProtectionPrice = -1L;
    private static long explosionProtectionPrice = -1L;
    private static long pvpDisablePrice = -1L;
    private static long blockInteractProtectionPrice = -1L;
    private static long blockEditProtectionPrice = -1L;
    private static long entityInteractProtectionPrice = -1L;
    private static int landChunkGroupSize = -1;
    private static boolean balanceSynced;
    private static boolean balanceEmpty = true;
    @Nullable
    private static String balanceText;
    private static int lastUpdateTotalChunks = 1;
    private static Map<String, Integer> lastProblems = Map.of();

    private ClientPricingCache() {
    }

    public static void update(
            long newClaimPrice,
            long newForceLoadUpkeepPrice,
            int newUpkeepPeriodMinutes,
            int newFreeChunks,
            int newClaimedChunks,
            boolean newBalanceSynced,
            boolean newBalanceEmpty,
            String newBalanceText,
            long newMobGriefProtectionPrice,
            long newExplosionProtectionPrice,
            long newPvpDisablePrice,
            long newBlockInteractProtectionPrice,
            long newBlockEditProtectionPrice,
            long newEntityInteractProtectionPrice,
            int newLandChunkGroupSize
    ) {
        claimPrice = newClaimPrice;
        forceLoadUpkeepPrice = newForceLoadUpkeepPrice;
        upkeepPeriodMinutes = newUpkeepPeriodMinutes;
        freeChunks = newFreeChunks;
        claimedChunks = newClaimedChunks;
        balanceSynced = newBalanceSynced;
        balanceEmpty = newBalanceEmpty;
        balanceText = newBalanceText;
        mobGriefProtectionPrice = newMobGriefProtectionPrice;
        explosionProtectionPrice = newExplosionProtectionPrice;
        pvpDisablePrice = newPvpDisablePrice;
        blockInteractProtectionPrice = newBlockInteractProtectionPrice;
        blockEditProtectionPrice = newBlockEditProtectionPrice;
        entityInteractProtectionPrice = newEntityInteractProtectionPrice;
        landChunkGroupSize = newLandChunkGroupSize;
    }

    public static long claimPrice() {
        return claimPrice;
    }

    public static long forceLoadUpkeepPrice() {
        return forceLoadUpkeepPrice;
    }

    public static int freeChunks() {
        return freeChunks;
    }

    public static int claimedChunks() {
        return claimedChunks;
    }

    public static boolean balanceEmpty() {
        return balanceEmpty;
    }

    public static long remainingFreeChunks() {
        return Math.max(0, freeChunks - claimedChunks);
    }

    /** Public alias of {@link #balanceComponent()} for use outside this class. */
    public static Component currentBalanceText() {
        return balanceComponent();
    }

    /** Public alias of {@link #effectivePriceComponent()} for use outside this class. */
    public static Component currentEffectiveClaimPrice() {
        return effectivePriceComponent();
    }

    /**
     * Projects the total price of claiming {@code additionalChunks} more chunks from now,
     * accounting for any remaining free-chunk allowance.
     */
    public static long projectedBulkClaimCopper(int additionalChunks) {
        if (additionalChunks <= 0 || claimPrice <= 0L) {
            return 0L;
        }
        long free = remainingFreeChunks();
        long billable = Math.max(0L, additionalChunks - free);
        return billable * claimPrice;
    }

    public static int landChunkGroupSize() {
        return landChunkGroupSize > 0 ? landChunkGroupSize : 5;
    }

    public static int upkeepPeriodMinutes() {
        return upkeepPeriodMinutes;
    }

    private static final Map<String, Long> DEFAULT_PROTECTION_PRICES = Map.of(
            "allow_mob_griefing", 10L,
            "allow_explosions", 10L,
            "allow_pvp", 5L,
            "block_interact_mode", 15L,
            "block_edit_mode", 15L,
            "entity_interact_mode", 15L
    );

    @Nullable
    public static Long protectionPrice(String propertyKey) {
        long raw = switch (propertyKey) {
            case "allow_mob_griefing" -> mobGriefProtectionPrice;
            case "allow_explosions" -> explosionProtectionPrice;
            case "allow_pvp" -> pvpDisablePrice;
            case "block_interact_mode" -> blockInteractProtectionPrice;
            case "block_edit_mode" -> blockEditProtectionPrice;
            case "entity_interact_mode" -> entityInteractProtectionPrice;
            default -> Long.MIN_VALUE;
        };
        return raw >= 0L ? raw : null;
    }

    @Nullable
    public static Long defaultProtectionPrice(String propertyKey) {
        return DEFAULT_PROTECTION_PRICES.get(propertyKey);
    }

    public static boolean protectionPricesSynced() {
        long[] livePrices = {
                mobGriefProtectionPrice,
                explosionProtectionPrice,
                pvpDisablePrice,
                blockInteractProtectionPrice,
                blockEditProtectionPrice,
                entityInteractProtectionPrice
        };
        for (long price : livePrices) {
            if (price < 0L) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSynced() {
        return claimPrice >= 0L
                && forceLoadUpkeepPrice >= 0L
                && upkeepPeriodMinutes > 0
                && balanceSynced
                && protectionPricesSynced();
    }

    public static boolean isLcClaimResult(String resultId) {
        return LC_CLAIM_RESULT_IDS.contains(resultId);
    }

    private record BarSegment(Component text, Color4I color) {
    }

    /** Paints the "claim <price> | balance <balance> | upkeep <price> <period>" line into FTB Chunks' bottom bar, left to right, tracking a running cursor since each segment's width depends on its text. */
    public static void renderBottomPanel(GuiGraphics graphics, Theme theme, int x, int y) {
        List<BarSegment> segments = List.of(
                new BarSegment(labelText("claim"), LABEL_COLOR),
                new BarSegment(spaceText(), LABEL_COLOR),
                new BarSegment(effectivePriceComponent(), VALUE_COLOR),
                new BarSegment(separatorText(), SEPARATOR_COLOR),
                new BarSegment(labelText("balance"), LABEL_COLOR),
                new BarSegment(spaceText(), LABEL_COLOR),
                new BarSegment(balanceComponent(), VALUE_COLOR),
                new BarSegment(separatorText(), SEPARATOR_COLOR),
                new BarSegment(labelText("upkeep"), LABEL_COLOR),
                new BarSegment(spaceText(), LABEL_COLOR),
                new BarSegment(priceComponent(forceLoadUpkeepPrice), VALUE_COLOR),
                new BarSegment(spaceText(), PERIOD_COLOR),
                new BarSegment(periodComponent(upkeepPeriodMinutes), PERIOD_COLOR)
        );

        int cursor = x;
        for (BarSegment segment : segments) {
            cursor = paintSegment(theme, graphics, cursor, y, segment.text(), segment.color());
        }
    }

    public static void noteChunkUpdate(int totalChunks, int changedChunks, Map<String, Integer> problems) {
        lastUpdateTotalChunks = totalChunks;
        lastProblems = problems;
    }

    /** Picks the right chat-line translation for a failed claim attempt, based on which shortfall result fired and how many chunks were involved. */
    public static MutableComponent claimProblemLine(String resultId) {
        if (MassClaimShortfallResult.RESULT_ID.equals(resultId)) {
            int count = lastProblems.getOrDefault(resultId, lastUpdateTotalChunks);
            return insufficientFundsBulkMessage(count);
        }
        if (ClaimShortfallResult.RESULT_ID.equals(resultId)) {
            int count = lastProblems.getOrDefault(resultId, 1);
            if (count > 1) {
                return Component.translatable(
                        "message.lc_claim_economy.insufficient_funds_bulk_claim",
                        effectivePriceComponent(),
                        count,
                        balanceComponent()
                );
            }
            return insufficientFundsMessage();
        }
        return Component.translatable(resultId);
    }

    public static MutableComponent insufficientFundsMessage() {
        return Component.translatable(
                ClaimShortfallResult.RESULT_ID,
                effectivePriceComponent(),
                balanceComponent()
        );
    }

    public static MutableComponent insufficientFundsBulkMessage(int chunkCount) {
        long unitPrice = nextClaimUnitPrice();
        return Component.translatable(
                MassClaimShortfallResult.RESULT_ID,
                priceComponent(unitPrice * Math.max(chunkCount, 1)),
                chunkCount,
                balanceComponent()
        );
    }

    /** 0 while the free-chunk allowance still covers the next claim, otherwise the full per-chunk price. */
    private static long nextClaimUnitPrice() {
        return claimedChunks < freeChunks ? 0L : claimPrice;
    }

    private static Component effectivePriceComponent() {
        return priceComponent(nextClaimUnitPrice());
    }

    private static int paintSegment(
            Theme theme,
            GuiGraphics graphics,
            int x,
            int y,
            Component text,
            Color4I color
    ) {
        theme.drawString(graphics, text, x, y, color, 0);
        return x + theme.getStringWidth(text);
    }

    private static Component labelText(String key) {
        return Component.translatable("gui.lc_claim_economy.label." + key);
    }

    private static Component separatorText() {
        return Component.translatable("gui.lc_claim_economy.separator");
    }

    private static Component spaceText() {
        return Component.literal(" ");
    }

    private static Component balanceComponent() {
        if (balanceEmpty) {
            return Component.translatable("message.lc_claim_economy.balance_empty");
        }
        return Component.literal(balanceText == null ? "" : balanceText);
    }

    private static Component priceComponent(long amount) {
        if (amount <= 0L) {
            return Component.translatable("gui.lc_claim_economy.price_free");
        }
        return CurrencyAmounts.fromCopper(amount).getText();
    }

    private record PeriodUnit(int minuteSize, String singularKey, String pluralKey) {
    }

    private static final List<PeriodUnit> LARGE_PERIOD_UNITS = List.of(
            new PeriodUnit(1440, "gui.lc_claim_economy.period.per_day", "gui.lc_claim_economy.period.per_days"),
            new PeriodUnit(60, "gui.lc_claim_economy.period.per_hour", "gui.lc_claim_economy.period.per_hours")
    );

    /** Collapses a minute count down to whichever unit divides it evenly (days, then hours, then minutes), matching or pluralizing the translation key as needed. */
    private static Component periodComponent(int minutes) {
        for (PeriodUnit unit : LARGE_PERIOD_UNITS) {
            if (minutes % unit.minuteSize() == 0) {
                int count = minutes / unit.minuteSize();
                return count == 1 ? Component.translatable(unit.singularKey()) : Component.translatable(unit.pluralKey(), count);
            }
        }
        return minutes == 1
                ? Component.translatable("gui.lc_claim_economy.period.per_minute")
                : Component.translatable("gui.lc_claim_economy.period.per_minutes", minutes);
    }
}
