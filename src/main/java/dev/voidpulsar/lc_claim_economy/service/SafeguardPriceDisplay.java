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

public final class SafeguardPriceDisplay {
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

        return switch (key) {
            case "allow_mob_griefing" -> LcClaimEconomyConfig.SERVER.mobGriefProtectionPrice.get();
            case "allow_explosions" -> LcClaimEconomyConfig.SERVER.explosionProtectionPrice.get();
            case "allow_pvp" -> LcClaimEconomyConfig.SERVER.pvpDisablePrice.get();
            case "block_interact_mode" -> LcClaimEconomyConfig.SERVER.blockInteractProtectionPrice.get();
            case "block_edit_mode" -> LcClaimEconomyConfig.SERVER.blockEditProtectionPrice.get();
            case "entity_interact_mode" -> LcClaimEconomyConfig.SERVER.entityInteractProtectionPrice.get();
            default -> null;
        };
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

        return switch (key) {
            case "allow_mob_griefing", "allow_explosions", "allow_pvp" ->
                    value instanceof Boolean enabled && !enabled;
            case "block_interact_mode", "block_edit_mode", "entity_interact_mode" ->
                    value instanceof PrivacyMode mode && mode != PrivacyMode.PUBLIC;
            default -> false;
        };
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
        return "allow_mob_griefing".equals(key)
                || "allow_explosions".equals(key)
                || "allow_pvp".equals(key);
    }

    public static Component formatPricePerChunk(long copper) {
        return CurrencyAmounts.fromCopper(copper).getText();
    }

    public static Component upkeepPeriodLabel() {
        int minutes = FMLEnvironment.dist == Dist.CLIENT ? ClientPricingCache.upkeepPeriodMinutes() : -1;
        if (minutes <= 0) {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                return formatUpkeepPeriodLabel(60);
            }
            minutes = LcClaimEconomyConfig.SERVER.upkeepPeriodMinutes.get();
        }
        return formatUpkeepPeriodLabel(minutes);
    }

    public static Component formatUpkeepPeriodLabel(int minutes) {
        if (minutes <= 1) {
            return Component.translatable("message.lc_claim_economy.upkeep_period.one_minute");
        }
        if (minutes < 60) {
            return Component.translatable("message.lc_claim_economy.upkeep_period.minutes", minutes);
        }
        if (minutes == 60) {
            return Component.translatable("message.lc_claim_economy.upkeep_period.one_hour");
        }
        if (minutes % 60 == 0) {
            return Component.translatable("message.lc_claim_economy.upkeep_period.hours", minutes / 60);
        }
        if (minutes == 1440) {
            return Component.translatable("message.lc_claim_economy.upkeep_period.one_day");
        }
        if (minutes % 1440 == 0) {
            return Component.translatable("message.lc_claim_economy.upkeep_period.days", minutes / 1440);
        }
        return Component.translatable("message.lc_claim_economy.upkeep_period.minutes", minutes);
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

    @Nullable
    public static String normalizePropertyKey(String configId) {
        if (configId == null || configId.isBlank()) {
            return null;
        }

        String key = configId;
        int colon = key.indexOf(':');
        if (colon >= 0) {
            key = key.substring(colon + 1);
        }
        int slash = key.lastIndexOf('/');
        if (slash >= 0) {
            key = key.substring(slash + 1);
        }
        // Config paths from FTB Library group configs are dot-separated
        // (e.g. "ftbteamsconfig.ftbchunks.allow_pvp").
        int dot = key.lastIndexOf('.');
        if (dot >= 0) {
            key = key.substring(dot + 1);
        }
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
            if (entry.status() == ConflictEntryStatus.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    /**
     * Incoming wars scale displayed base upkeep by {@code 1 + sum(i=0..k-1) l^i} for {@code k} declarers.
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
