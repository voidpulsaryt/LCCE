package dev.voidpulsar.lc_claim_economy.opc;

import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import xaero.pac.common.player.config.PlayerConfigConstants;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigOptionSpecAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

/**
 * OP&C equivalent of the FTB {@code SafeguardPricing}, adapted to OP&C's
 * per-owner protection model. FTB Chunks has a handful of team-level
 * public/private toggles; OP&C instead has per-owner "exceptions" that name
 * which player group (Nobody/Party/Allies/Everyone/a custom group) is let
 * through a given protection. The mapping used here treats "exempts
 * everyone" ({@link PlayerConfigConstants#EVERYONE_EXCEPTION_ID}, or the
 * boolean equivalent being {@code true}) as equivalent to FTB's
 * {@code PrivacyMode.PUBLIC} (free), and anything tighter (Nobody, Party,
 * Allies, or a custom group) as equivalent to a non-public mode (billable) -
 * mirroring how a locked-down claim costs more on the FTB side. This is a
 * simplification: OP&C's per-property exceptions are far more granular than
 * FTB Chunks' six toggles, and there's no way to fully preserve that nuance
 * while reusing the same flat per-property prices, but it keeps the two
 * integrations charging on the same basic principle ("more restrictive
 * costs more").
 * <p>
 * Land-chunk pricing intentionally mirrors the FTB side too: only the
 * block-edit and block-interact ("item use") exceptions are priced, since
 * those are the only two land protections FTB Chunks itself exposes.
 */
final class OpcProtectionPricing {
    private OpcProtectionPricing() {
    }

    static long calculateBuildBasePrice(IPlayerConfigAPI config) {
        if (!isProtectionEnabled(config)) {
            return 0L;
        }
        long total = 0L;
        if (!groupCoversEveryone(config, PlayerConfigOptions.CLAIM_EXCEPTION_BLOCKS_BY_MOBS)) {
            total += LcClaimEconomyConfig.SERVER.mobGriefProtectionPrice.get();
        }
        if (!flagCoversEveryone(config, PlayerConfigOptions.CLAIM_EXCEPTION_BLOCKS_BY_EXPLOSIONS)) {
            total += LcClaimEconomyConfig.SERVER.explosionProtectionPrice.get();
        }
        if (!flagCoversEveryone(config, PlayerConfigOptions.CLAIM_EXCEPTION_PLAYERS_BY_PLAYERS)) {
            total += LcClaimEconomyConfig.SERVER.pvpDisablePrice.get();
        }
        if (!groupCoversEveryone(config, PlayerConfigOptions.CLAIM_EXCEPTION_ITEM_USE)) {
            total += LcClaimEconomyConfig.SERVER.blockInteractProtectionPrice.get();
        }
        if (!groupCoversEveryone(config, PlayerConfigOptions.CLAIM_EXCEPTION_BLOCKS_BY_PLAYERS)) {
            total += LcClaimEconomyConfig.SERVER.blockEditProtectionPrice.get();
        }
        if (!groupCoversEveryone(config, PlayerConfigOptions.CLAIM_EXCEPTION_ENTITIES_BY_PLAYERS)) {
            total += LcClaimEconomyConfig.SERVER.entityInteractProtectionPrice.get();
        }
        return total;
    }

    static long calculateLandBasePrice(IPlayerConfigAPI config) {
        if (!isProtectionEnabled(config)) {
            return 0L;
        }
        long total = 0L;
        if (!groupCoversEveryone(config, PlayerConfigOptions.CLAIM_EXCEPTION_ITEM_USE)) {
            total += LcClaimEconomyConfig.SERVER.blockInteractProtectionPrice.get();
        }
        if (!groupCoversEveryone(config, PlayerConfigOptions.CLAIM_EXCEPTION_BLOCKS_BY_PLAYERS)) {
            total += LcClaimEconomyConfig.SERVER.blockEditProtectionPrice.get();
        }
        return total;
    }

    private static boolean isProtectionEnabled(IPlayerConfigAPI config) {
        // Treat an unset value (null) as "protected" - matches OP&C's own default for
        // this option, and an absent option shouldn't silently disable pricing.
        Boolean enabled = config.getEffective(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS);
        return enabled == null || enabled;
    }

    /** True when the exception group named by this option is the built-in "everyone" group. */
    private static boolean groupCoversEveryone(IPlayerConfigAPI config, IPlayerConfigOptionSpecAPI<String> option) {
        String groupId = config.getEffective(option);
        return PlayerConfigConstants.EVERYONE_EXCEPTION_ID.equals(groupId);
    }

    private static boolean flagCoversEveryone(IPlayerConfigAPI config, IPlayerConfigOptionSpecAPI<Boolean> option) {
        Boolean value = config.getEffective(option);
        return value != null && value;
    }
}
