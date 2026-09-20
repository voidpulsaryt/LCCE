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
 * Configurable order in which protections are disabled when upkeep cannot be
 * paid, and the inverse order used when restoring them at upkeep.
 */
public final class SafeguardDismantleSequence {
    private static final List<String> DEFAULT_BUILD_ORDER = List.of(
            "entity_interact_mode",
            "block_edit_mode",
            "block_interact_mode",
            "allow_mob_griefing",
            "allow_explosions",
            "allow_pvp"
    );

    private static final List<String> DEFAULT_LAND_ORDER = List.of(
            "land_block_edit_mode",
            "land_block_interact_mode"
    );

    private SafeguardDismantleSequence() {
    }

    public static List<TeamProperty<?>> buildOrder() {
        return resolveOrder(LcClaimEconomyConfig.SERVER.protectionDismantleOrderBuild.get(),
                DEFAULT_BUILD_ORDER, SafeguardPricing.BUILD_PROTECTION_PROPERTIES);
    }

    public static List<TeamProperty<?>> landOrder() {
        return resolveOrder(LcClaimEconomyConfig.SERVER.protectionDismantleOrderLand.get(),
                DEFAULT_LAND_ORDER, LandProperties.ALL);
    }

    public static List<TeamProperty<?>> fullDismantleOrder() {
        // Land protections are stripped first; build protections after.
        // Deduplicate so a property that appears in both configured lists
        // is only processed once.
        List<TeamProperty<?>> order = new ArrayList<>(landOrder());
        for (TeamProperty<?> p : buildOrder()) {
            if (!order.contains(p)) {
                order.add(p);
            }
        }
        return order;
    }

    public static List<TeamProperty<?>> restoreOrder() {
        List<TeamProperty<?>> order = new ArrayList<>(fullDismantleOrder());
        java.util.Collections.reverse(order);
        return order;
    }

    public static int dismantleIndex(TeamProperty<?> property) {
        List<TeamProperty<?>> order = fullDismantleOrder();
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).equals(property)) {
                return i;
            }
        }
        return -1;
    }

    public static Comparator<String> restorePropertyKeyComparator() {
        Map<String, Integer> index = new HashMap<>();
        List<TeamProperty<?>> restore = restoreOrder();
        for (int i = 0; i < restore.size(); i++) {
            index.put(SafeguardPricing.propertyKey(restore.get(i)), i);
        }
        return Comparator.comparingInt(key -> index.getOrDefault(key, Integer.MAX_VALUE));
    }

    private static List<TeamProperty<?>> resolveOrder(
            List<? extends String> configured,
            List<String> defaults,
            Collection<? extends TeamProperty<?>> allowedFallback
    ) {
        List<String> keys = configured.isEmpty() ? defaults : new ArrayList<>(configured);
        List<TeamProperty<?>> resolved = new ArrayList<>();
        for (String key : keys) {
            TeamProperty<?> property = findProperty(key);
            if (property != null && !resolved.contains(property)) {
                resolved.add(property);
            }
        }
        // Fill in any properties from the allowed set that aren't explicitly
        // listed — this prevents cross-contamination between build and land lists.
        for (TeamProperty<?> property : allowedFallback) {
            if (!resolved.contains(property)) {
                resolved.add(property);
            }
        }
        return resolved;
    }

    private static TeamProperty<?> findProperty(String key) {
        for (TeamProperty<?> property : SafeguardPricing.PROTECTION_PROPERTIES) {
            if (SafeguardPricing.propertyKey(property).equals(key)) {
                return property;
            }
        }
        return null;
    }
}
