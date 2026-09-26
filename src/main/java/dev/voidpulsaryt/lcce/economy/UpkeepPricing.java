package dev.voidpulsaryt.lcce.economy;

import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.region.PermissionTier;
import dev.voidpulsaryt.lcce.region.ProtectionLineItem;
import dev.voidpulsaryt.lcce.region.Region;
import dev.voidpulsaryt.lcce.region.RegionProtectionSettings;

import java.math.BigInteger;

/**
 * Per-period upkeep pricing for a region's protection line items, and whether the team currently
 * wants ("intends") each one versus whether it's actually in effect right now (see
 * {@link Region#dismantled()}).
 */
public final class UpkeepPricing {

    private UpkeepPricing() {}

    /** Whether the team's configured settings call for this protection to be on at all. */
    public static boolean isIntended(Region region, ProtectionLineItem item) {
        RegionProtectionSettings s = region.settings();
        return switch (item) {
            case MOB_GRIEFING -> !s.allowMobGriefing();
            case EXPLOSIONS -> !s.allowExplosions();
            case PVP -> !s.allowPvp();
            case INTERACT_TIER -> s.interactTier() != PermissionTier.PUBLIC;
            case EDIT_TIER -> s.editTier() != PermissionTier.PUBLIC;
        };
    }

    /** Whether this protection is intended AND currently affordable/paid-for (not dismantled). */
    public static boolean isActive(Region region, ProtectionLineItem item) {
        return isIntended(region, item) && !region.dismantled().contains(item);
    }

    /** Per-period price of this line item, if the team wants it. Zero if not intended at all. */
    public static BigInteger costOf(Region region, ProtectionLineItem item) {
        if (!isIntended(region, item)) {
            return BigInteger.ZERO;
        }
        RegionProtectionSettings s = region.settings();
        return switch (item) {
            case MOB_GRIEFING -> BigInteger.valueOf(LCCEConfig.UPKEEP_MOB_GRIEFING_PRICE.get());
            case EXPLOSIONS -> BigInteger.valueOf(LCCEConfig.UPKEEP_EXPLOSIONS_PRICE.get());
            case PVP -> BigInteger.valueOf(LCCEConfig.UPKEEP_PVP_PRICE.get());
            case INTERACT_TIER -> BigInteger.valueOf(LCCEConfig.UPKEEP_PRICE_PER_TIER_LEVEL.get() * s.interactTier().ordinal());
            case EDIT_TIER -> BigInteger.valueOf(LCCEConfig.UPKEEP_PRICE_PER_TIER_LEVEL.get() * s.editTier().ordinal());
        };
    }
}
