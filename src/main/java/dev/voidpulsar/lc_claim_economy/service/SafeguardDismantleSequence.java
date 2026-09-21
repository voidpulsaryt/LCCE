package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.teams.LandProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server owners can reorder which protection drops first when a team can't cover
 * upkeep, via {@code protectionDismantleOrderBuild}/{@code ...Land}. This class turns
 * that config into concrete property lists, in both the dismantle direction and its
 * mirror-image restore direction.
 */
public final class SafeguardDismantleSequence {
    private static final List<String> FALLBACK_BUILD_ORDER = List.of(
            "entity_interact_mode",
            "block_edit_mode",
            "block_interact_mode",
            "allow_mob_griefing",
            "allow_explosions",
            "allow_pvp"
    );

    private static final List<String> FALLBACK_LAND_ORDER = List.of(
            "land_block_edit_mode",
            "land_block_interact_mode"
    );

    private SafeguardDismantleSequence() {
    }

    public static List<TeamProperty<?>> buildOrder() {
        return resolveConfiguredOrder(LcClaimEconomyConfig.SERVER.protectionDismantleOrderBuild.get(),
                FALLBACK_BUILD_ORDER, SafeguardPricing.BUILD_PROTECTION_PROPERTIES);
    }

    public static List<TeamProperty<?>> landOrder() {
        return resolveConfiguredOrder(LcClaimEconomyConfig.SERVER.protectionDismantleOrderLand.get(),
                FALLBACK_LAND_ORDER, LandProperties.ALL);
    }

    /**
     * Land goes first, build second. Within each list, duplicates are dropped so a
     * property that's (mis)configured into both the build and land order only gets
     * dismantled once instead of being processed twice.
     */
    public static List<TeamProperty<?>> fullDismantleOrder() {
        List<TeamProperty<?>> combined = new ArrayList<>(landOrder());
        for (TeamProperty<?> buildProperty : buildOrder()) {
            if (!combined.contains(buildProperty)) {
                combined.add(buildProperty);
            }
        }
        return combined;
    }

    /** Whatever was dismantled last comes back first - simple reversal of {@link #fullDismantleOrder()}. */
    public static List<TeamProperty<?>> restoreOrder() {
        List<TeamProperty<?>> reversed = new ArrayList<>(fullDismantleOrder());
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    public static int dismantleIndex(TeamProperty<?> property) {
        return fullDismantleOrder().indexOf(property);
    }

    public static Comparator<String> restorePropertyKeyComparator() {
        List<TeamProperty<?>> restoreSequence = restoreOrder();
        Map<String, Integer> rank = new HashMap<>();
        for (int position = 0; position < restoreSequence.size(); position++) {
            rank.put(SafeguardPricing.propertyKey(restoreSequence.get(position)), position);
        }
        return Comparator.comparingInt(key -> rank.getOrDefault(key, Integer.MAX_VALUE));
    }

    /**
     * Builds a property list from a config key list, falling back to the shipped
     * default order when the server owner hasn't set one. Anything in {@code
     * completeSet} that the configured/default list doesn't mention is appended
     * afterward, so build and land properties never bleed into each other's order
     * just because one list is incomplete.
     */
    private static List<TeamProperty<?>> resolveConfiguredOrder(
            List<? extends String> configuredKeys,
            List<String> fallbackKeys,
            Collection<? extends TeamProperty<?>> completeSet
    ) {
        List<String> keysToUse = configuredKeys.isEmpty() ? fallbackKeys : new ArrayList<>(configuredKeys);
        List<TeamProperty<?>> ordered = new ArrayList<>();
        for (String key : keysToUse) {
            TeamProperty<?> property = findProtectionProperty(key);
            if (property != null && !ordered.contains(property)) {
                ordered.add(property);
            }
        }
        for (TeamProperty<?> property : completeSet) {
            if (!ordered.contains(property)) {
                ordered.add(property);
            }
        }
        return ordered;
    }

    private static TeamProperty<?> findProtectionProperty(String key) {
        for (TeamProperty<?> property : SafeguardPricing.PROTECTION_PROPERTIES) {
            if (SafeguardPricing.propertyKey(property).equals(key)) {
                return property;
            }
        }
        return null;
    }
}
