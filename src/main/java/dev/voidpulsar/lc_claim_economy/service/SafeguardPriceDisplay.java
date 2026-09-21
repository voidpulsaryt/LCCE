package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsar.lc_claim_economy.client.ClientPricingCache;
import dev.voidpulsar.lc_claim_economy.client.ClientConflictState;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.network.ConflictEntryStatus;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

public final class SafeguardPriceDisplay {

    /** Boolean-style toggles where {@code true} means "protection off". */
    private static final Set<String> ALLOW_STYLE_KEYS =
            Set.of("allow_mob_griefing", "allow_explosions", "allow_pvp");

    /** Privacy-mode toggles where anything but {@code PUBLIC} is a billable restriction. */
    private static final Set<String> PRIVACY_STYLE_KEYS =
            Set.of("block_interact_mode", "block_edit_mode", "entity_interact_mode");

    /** Server-side config price lookup, keyed the same as {@link #ALLOW_STYLE_KEYS} / {@link #PRIVACY_STYLE_KEYS} combined. */
    private static final Map<String, Supplier<Long>> SERVER_PRICE_LOOKUP = Map.ofEntries(
            Map.entry("allow_mob_griefing", () -> LcClaimEconomyConfig.SERVER.mobGriefProtectionPrice.get()),
            Map.entry("allow_explosions", () -> LcClaimEconomyConfig.SERVER.explosionProtectionPrice.get()),
            Map.entry("allow_pvp", () -> LcClaimEconomyConfig.SERVER.pvpDisablePrice.get()),
            Map.entry("block_interact_mode", () -> LcClaimEconomyConfig.SERVER.blockInteractProtectionPrice.get()),
            Map.entry("block_edit_mode", () -> LcClaimEconomyConfig.SERVER.blockEditProtectionPrice.get()),
            Map.entry("entity_interact_mode", () -> LcClaimEconomyConfig.SERVER.entityInteractProtectionPrice.get())
    );

    private SafeguardPriceDisplay() {
    }

    @Nullable
    public static Long pricePerChunkForConfigId(String configId) {
        String key = baseProtectionKey(configId);
        if (key == null) {
            return null;
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            Long synced = ClientPricingCache.protectionPrice(key);
            if (synced != null) {
                return synced;
            }
            return ClientPricingCache.defaultProtectionPrice(key);
        }

        Supplier<Long> priceSource = SERVER_PRICE_LOOKUP.get(key);
        return priceSource != null ? priceSource.get() : null;
    }

    @Nullable
    public static Long pricePerChunkForProperty(TeamProperty<?> property) {
        return pricePerChunkForConfigId(SafeguardPricing.propertyKey(property));
    }

    public static boolean isProtectionConfigId(String configId) {
        return pricePerChunkForConfigId(configId) != null;
    }

    public static boolean isActiveBillableSetting(String configId, Object value) {
        String key = baseProtectionKey(configId);
        if (key == null) {
            return false;
        }
        if (ALLOW_STYLE_KEYS.contains(key)) {
            return value instanceof Boolean enabled && !enabled;
        }
        if (PRIVACY_STYLE_KEYS.contains(key)) {
            return value instanceof PrivacyMode mode && mode != PrivacyMode.PUBLIC;
        }
        return false;
    }

    /**
     * For {@code allow_*} booleans, FTB colors {@code true} green even though
     * {@code true} means the protection is off. Green = protection active.
     */
    @Nullable
    public static TextColor protectionAllowBooleanColor(String configId, Object value) {
        if (!(value instanceof Boolean) || !isAllowStyleProtectionKey(configId)) {
            return null;
        }
        return TextColor.fromLegacyFormat(
                isActiveBillableSetting(configId, value) ? ChatFormatting.GREEN : ChatFormatting.RED
        );
    }

    public static boolean isAllowStyleProtectionKey(String configId) {
        String key = baseProtectionKey(configId);
        return key != null && ALLOW_STYLE_KEYS.contains(key);
    }

    public static Component formatPricePerChunk(long copper) {
        return CurrencyAmounts.fromCopper(copper).getText();
    }

    public static Component upkeepPeriodLabel() {
        int minutes = FMLEnvironment.dist == Dist.CLIENT
                ? clientUpkeepMinutesOrDefault()
                : LcClaimEconomyConfig.SERVER.upkeepPeriodMinutes.get();
        return formatUpkeepPeriodLabel(minutes);
    }

    /** Synced upkeep-period minutes, falling back to a flat 60 (never the config default) when nothing has synced yet. */
    private static int clientUpkeepMinutesOrDefault() {
        int synced = ClientPricingCache.upkeepPeriodMinutes();
        return synced > 0 ? synced : 60;
    }

    /**
     * Rules are evaluated in order and the first match wins, mirroring the historical
     * if-chain exactly - including that a multiple of 1440 is always also a multiple of
     * 60, so the "one_day"/"days" rules below never actually fire before the "hours" rule
     * does. Kept in place rather than pruned so the resolution order is provably unchanged.
     */
    private static final List<UpkeepPeriodRule> UPKEEP_PERIOD_RULES = List.of(
            new UpkeepPeriodRule(minutes -> minutes <= 1,
                    minutes -> Component.translatable("message.lc_claim_economy.upkeep_period.one_minute")),
            new UpkeepPeriodRule(minutes -> minutes < 60,
                    minutes -> Component.translatable("message.lc_claim_economy.upkeep_period.minutes", minutes)),
            new UpkeepPeriodRule(minutes -> minutes == 60,
                    minutes -> Component.translatable("message.lc_claim_economy.upkeep_period.one_hour")),
            new UpkeepPeriodRule(minutes -> minutes % 60 == 0,
                    minutes -> Component.translatable("message.lc_claim_economy.upkeep_period.hours", minutes / 60)),
            new UpkeepPeriodRule(minutes -> minutes == 1440,
                    minutes -> Component.translatable("message.lc_claim_economy.upkeep_period.one_day")),
            new UpkeepPeriodRule(minutes -> minutes % 1440 == 0,
                    minutes -> Component.translatable("message.lc_claim_economy.upkeep_period.days", minutes / 1440))
    );

    public static Component formatUpkeepPeriodLabel(int minutes) {
        for (UpkeepPeriodRule rule : UPKEEP_PERIOD_RULES) {
            if (rule.matches().test(minutes)) {
                return rule.render().apply(minutes);
            }
        }
        return Component.translatable("message.lc_claim_economy.upkeep_period.minutes", minutes);
    }

    private record UpkeepPeriodRule(IntPredicate matches, IntFunction<Component> render) {
    }

    /**
     * Normalizes a config id and strips the {@code land_} prefix so build and
     * land protections resolve to the same price entry.
     */
    @Nullable
    public static String baseProtectionKey(String configId) {
        String key = normalizePropertyKey(configId);
        if (key == null) {
            return null;
        }
        return key.startsWith("land_") ? key.substring("land_".length()) : key;
    }

    /**
     * Strips a leading {@code namespace:} (first colon only), then descends into the last
     * {@code /}-separated segment and finally the last {@code .}-separated segment of that -
     * config paths from FTB Library group configs are dot-separated (e.g.
     * {@code "ftbteamsconfig.ftbchunks.allow_pvp"}).
     */
    @Nullable
    public static String normalizePropertyKey(String configId) {
        if (configId == null || configId.isBlank()) {
            return null;
        }

        int colon = configId.indexOf(':');
        String withoutNamespace = colon >= 0 ? configId.substring(colon + 1) : configId;

        String[] pathSegments = withoutNamespace.split("/", -1);
        String lastPathSegment = pathSegments[pathSegments.length - 1];

        String[] groupSegments = lastPathSegment.split("\\.", -1);
        String key = groupSegments[groupSegments.length - 1];

        return key.isBlank() ? null : key;
    }

    /**
     * Resolves the protection property key for an FTB config entry. Prefer
     * {@code config.id} when it matches a known protection property: land
     * entries live in the {@code lc_claim_economy} subgroup and their group path
     * alone ({@code lc_claim_economy}) must not be used for pending lookups.
     */
    public static String protectionPropertyKey(@Nullable String configId, @Nullable String configPath) {
        // Land entries live under ftbteamsconfig.lc_claim_economy.* — prefer the
        // path segment so we never confuse them with build keys like
        // block_edit_mode (land_block_edit_mode ends with that suffix).
        if (configPath != null && !configPath.isBlank()) {
            String fromPath = normalizePropertyKey(configPath);
            if (fromPath != null && fromPath.startsWith("land_") && isProtectionPropertyKey(fromPath)) {
                return fromPath;
            }
        }

        String fromId = normalizePropertyKey(configId);
        if (isProtectionPropertyKey(fromId)) {
            return fromId;
        }

        String fromPath = normalizePropertyKey(configPath);
        if (isProtectionPropertyKey(fromPath)) {
            return fromPath;
        }

        if (configPath != null && !configPath.isBlank()) {
            return configPath;
        }
        return configId != null ? configId : "";
    }

    public static boolean isProtectionPropertyKey(@Nullable String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        for (TeamProperty<?> property : SafeguardPricing.PROTECTION_PROPERTIES) {
            if (SafeguardPricing.propertyKey(property).equals(key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLandProtectionPropertyKey(@Nullable String configId) {
        String key = normalizePropertyKey(configId);
        return key != null && key.startsWith("land_");
    }

    public static int landChunkGroupSize() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return ClientPricingCache.landChunkGroupSize();
        }
        return SafeguardPricing.landChunkGroupSize();
    }

    public static int incomingWarCount() {
        if (FMLEnvironment.dist != Dist.CLIENT || !ClientConflictState.warModuleEnabled()) {
            return 0;
        }
        int count = 0;
        for (var entry : ClientConflictState.incoming()) {
            if (entry.conflictStatus() == ConflictEntryStatus.ENGAGED) {
                count++;
            }
        }
        return count;
    }

    /**
     * Incoming wars scale displayed base upkeep by {@code 1 + k * s} for {@code k} declarers
     * and step {@code s} — see {@link ConflictBillingMath}.
     */
    public static long effectiveProtectionPrice(long baseCopper) {
        if (baseCopper <= 0) {
            return baseCopper;
        }
        int incoming = incomingWarCount();
        if (incoming <= 0) {
            return baseCopper;
        }
        double factor = ConflictBillingMath.totalUpkeepFactor(incoming, ClientConflictState.warCostMultiplier());
        return (long) Math.floor(baseCopper * factor);
    }

    public static String incomingWarFactorLabel() {
        if (FMLEnvironment.dist != Dist.CLIENT || !ClientConflictState.warModuleEnabled()) {
            return "1";
        }
        int k = incomingWarCount();
        if (k <= 0) {
            return "1";
        }
        return ConflictBillingMath.formatTotalUpkeepFactor(k, ClientConflictState.warCostMultiplier());
    }

    public static boolean showsIncomingWarSurcharge() {
        return incomingWarCount() > 0;
    }
}
