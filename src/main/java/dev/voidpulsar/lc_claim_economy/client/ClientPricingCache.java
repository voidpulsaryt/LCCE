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
import java.util.Map;
import java.util.Set;

public final class ClientPricingCache {
    public static final Set<String> LC_CLAIM_RESULT_IDS = Set.of(
            ClaimShortfallResult.RESULT_ID,
            MassClaimShortfallResult.RESULT_ID,
            "message.lc_claim_economy.claim_rank_denied"
    );

    // Matches FTB Chunks' own "Claimed: X / Y" and "Force loaded: X / Y" text exactly:
    // white labels, vanilla Minecraft green (ChatFormatting.GREEN) for values. No
    // background/border box, since this renders inside FTB's own bottom bar
    // (ChunkScreen$CustomBottomPanel), which already provides the panel background -
    // adding our own on top just looked like a mismatched floating card.
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
            long claim,
            long forceLoadUpkeep,
            int upkeepPeriod,
            int free,
            int claimed,
            boolean syncedBalance,
            boolean emptyBalance,
            String balance,
            long mobGrief,
            long explosion,
            long pvpDisable,
            long blockInteract,
            long blockEdit,
            long entityInteract,
            int landGroupSize
    ) {
        claimPrice = claim;
        forceLoadUpkeepPrice = forceLoadUpkeep;
        upkeepPeriodMinutes = upkeepPeriod;
        freeChunks = free;
        claimedChunks = claimed;
        balanceSynced = syncedBalance;
        balanceEmpty = emptyBalance;
        balanceText = balance;
        mobGriefProtectionPrice = mobGrief;
        explosionProtectionPrice = explosion;
        pvpDisablePrice = pvpDisable;
        blockInteractProtectionPrice = blockInteract;
        blockEditProtectionPrice = blockEdit;
        entityInteractProtectionPrice = entityInteract;
        landChunkGroupSize = landGroupSize;
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

    /** Public alias of {@link #formatBalance()} for use outside this class. */
    public static Component currentBalanceText() {
        return formatBalance();
    }

    /** Public alias of {@link #formatEffectiveClaimPrice()} for use outside this class. */
    public static Component currentEffectiveClaimPrice() {
        return formatEffectiveClaimPrice();
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

    @Nullable
    public static Long protectionPrice(String propertyKey) {
        return switch (propertyKey) {
            case "allow_mob_griefing" -> mobGriefProtectionPrice >= 0L ? mobGriefProtectionPrice : null;
            case "allow_explosions" -> explosionProtectionPrice >= 0L ? explosionProtectionPrice : null;
            case "allow_pvp" -> pvpDisablePrice >= 0L ? pvpDisablePrice : null;
            case "block_interact_mode" -> blockInteractProtectionPrice >= 0L ? blockInteractProtectionPrice : null;
            case "block_edit_mode" -> blockEditProtectionPrice >= 0L ? blockEditProtectionPrice : null;
            case "entity_interact_mode" -> entityInteractProtectionPrice >= 0L ? entityInteractProtectionPrice : null;
            default -> null;
        };
    }

    @Nullable
    public static Long defaultProtectionPrice(String propertyKey) {
        return switch (propertyKey) {
            case "allow_mob_griefing" -> 10L;
            case "allow_explosions" -> 10L;
            case "allow_pvp" -> 5L;
            case "block_interact_mode" -> 15L;
            case "block_edit_mode" -> 15L;
            case "entity_interact_mode" -> 15L;
            default -> null;
        };
    }

    public static boolean protectionPricesSynced() {
        return mobGriefProtectionPrice >= 0L
                && explosionProtectionPrice >= 0L
                && pvpDisablePrice >= 0L
                && blockInteractProtectionPrice >= 0L
                && blockEditProtectionPrice >= 0L
                && entityInteractProtectionPrice >= 0L;
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

    public static void renderBottomPanel(GuiGraphics graphics, Theme theme, int x, int y) {
        int cursor = x;
        cursor = drawSegment(theme, graphics, cursor, y, label("claim"), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, space(), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, formatEffectiveClaimPrice(), VALUE_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, separator(), SEPARATOR_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, label("balance"), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, space(), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, formatBalance(), VALUE_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, separator(), SEPARATOR_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, label("upkeep"), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, space(), LABEL_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, formatPrice(forceLoadUpkeepPrice), VALUE_COLOR);
        cursor = drawSegment(theme, graphics, cursor, y, space(), PERIOD_COLOR);
        drawSegment(theme, graphics, cursor, y, formatUpkeepPeriod(upkeepPeriodMinutes), PERIOD_COLOR);
    }

    public static void noteChunkUpdate(int totalChunks, int changedChunks, Map<String, Integer> problems) {
        lastUpdateTotalChunks = totalChunks;
        lastProblems = problems;
    }

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
                        formatEffectiveClaimPrice(),
                        count,
                        formatBalance()
                );
            }
            return insufficientFundsMessage();
        }
        return Component.translatable(resultId);
    }

    public static MutableComponent insufficientFundsMessage() {
        return Component.translatable(
                ClaimShortfallResult.RESULT_ID,
                formatEffectiveClaimPrice(),
                formatBalance()
        );
    }

    public static MutableComponent insufficientFundsBulkMessage(int chunkCount) {
        long unitPrice = nextClaimUnitPrice();
        return Component.translatable(
                MassClaimShortfallResult.RESULT_ID,
                formatPrice(unitPrice * Math.max(chunkCount, 1)),
                chunkCount,
                formatBalance()
        );
    }

    private static long nextClaimUnitPrice() {
        return claimedChunks < freeChunks ? 0L : claimPrice;
    }

    private static Component formatEffectiveClaimPrice() {
        return formatPrice(nextClaimUnitPrice());
    }

    private static int drawSegment(
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

    private static Component label(String key) {
        return Component.translatable("gui.lc_claim_economy.label." + key);
    }

    private static Component separator() {
        return Component.translatable("gui.lc_claim_economy.separator");
    }

    private static Component space() {
        return Component.literal(" ");
    }

    private static Component formatBalance() {
        if (balanceEmpty) {
            return Component.translatable("message.lc_claim_economy.balance_empty");
        }
        return Component.literal(balanceText == null ? "" : balanceText);
    }

    private static Component formatPrice(long amount) {
        if (amount <= 0L) {
            return Component.translatable("gui.lc_claim_economy.price_free");
        }
        return CurrencyAmounts.fromCopper(amount).getText();
    }

    private static Component formatUpkeepPeriod(int minutes) {
        if (minutes % 1440 == 0) {
            int days = minutes / 1440;
            return days == 1
                    ? Component.translatable("gui.lc_claim_economy.period.per_day")
                    : Component.translatable("gui.lc_claim_economy.period.per_days", days);
        }
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours == 1
                    ? Component.translatable("gui.lc_claim_economy.period.per_hour")
                    : Component.translatable("gui.lc_claim_economy.period.per_hours", hours);
        }
        return minutes == 1
                ? Component.translatable("gui.lc_claim_economy.period.per_minute")
                : Component.translatable("gui.lc_claim_economy.period.per_minutes", minutes);
    }
}
