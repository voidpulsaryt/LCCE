package dev.voidpulsar.lc_claim_economy.client;

import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPriceDisplay;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPricing;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ClientQueuedChanges {
    private static Map<String, String> pendingProperties = Map.of();
    private static Set<String> pendingForceLoads = Set.of();
    private static Set<String> pendingForceUnloads = Set.of();
    private static Set<String> pendingLandChunks = Set.of();
    private static Set<String> pendingBuildChunks = Set.of();

    private ClientQueuedChanges() {
    }

    public static void update(
            Map<String, String> properties,
            Set<String> forceLoads,
            Set<String> forceUnloads,
            Set<String> landChunks,
            Set<String> buildChunks
    ) {
        pendingProperties = Map.copyOf(properties);
        pendingForceLoads = Set.copyOf(forceLoads);
        pendingForceUnloads = Set.copyOf(forceUnloads);
        pendingLandChunks = Set.copyOf(landChunks);
        pendingBuildChunks = Set.copyOf(buildChunks);
        MapManager.getInstance().ifPresent(manager -> manager.updateAllRegions(false));
    }

    public static boolean hasPendingProperty(String propertyId) {
        return resolvePendingPropertyKey(propertyId) != null;
    }

    public static boolean hasVisiblePendingProperty(String propertyId, Object currentValue) {
        if (!hasPendingProperty(propertyId)) {
            return false;
        }
        return !Objects.equals(currentValue, getDisplayValue(propertyId, currentValue));
    }

    @Nullable
    public static String resolvePendingPropertyKey(String propertyId) {
        String normalized = SafeguardPriceDisplay.normalizePropertyKey(propertyId);
        if (normalized != null && pendingProperties.containsKey(normalized)) {
            return normalized;
        }

        return pendingProperties.keySet().stream()
                .filter(pendingKey -> matchesPendingPropertyKey(propertyId, normalized, pendingKey))
                .findFirst()
                .orElse(null);
    }

    private static boolean matchesPendingPropertyKey(
            String propertyId,
            @Nullable String normalized,
            String pendingKey
    ) {
        return propertyId.equals(pendingKey) || pendingKey.equals(normalized) || propertyId.endsWith("." + pendingKey);
    }

    @Nullable
    public static String getPendingProperty(String propertyId) {
        String key = resolvePendingPropertyKey(propertyId);
        return key == null ? null : pendingProperties.get(key);
    }

    public static boolean isPendingForceLoad(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return containsChunkKey(pendingForceLoads, dimension, x, z);
    }

    public static boolean isPendingForceUnload(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return containsChunkKey(pendingForceUnloads, dimension, x, z);
    }

    public static boolean isPendingLandChunk(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return containsChunkKey(pendingLandChunks, dimension, x, z);
    }

    public static boolean isPendingBuildChunk(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return containsChunkKey(pendingBuildChunks, dimension, x, z);
    }

    private static boolean containsChunkKey(Set<String> keys, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, int x, int z) {
        return keys.contains(ChunkCoordKeyClient.encode(dimension.location(), x, z));
    }

    @SuppressWarnings("unchecked")
    public static <T> T getDisplayValue(TeamProperty<T> property, T currentValue) {
        String pending = getPendingProperty(SafeguardPricing.propertyKey(property));
        if (pending == null) {
            return currentValue;
        }
        return SafeguardPricing.deserializePropertyValue(property, pending, currentValue);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object getDisplayValue(String propertyId, Object currentValue) {
        String pendingKey = resolvePendingPropertyKey(propertyId);
        if (pendingKey == null) {
            return currentValue;
        }

        TeamProperty property = findProperty(pendingKey);
        if (property == null) {
            return currentValue;
        }

        String pending = pendingProperties.get(pendingKey);
        if (pending == null) {
            return currentValue;
        }
        return SafeguardPricing.deserializePropertyValue(property, pending, currentValue);
    }

    @Nullable
    private static TeamProperty<?> findProperty(String propertyId) {
        String normalized = SafeguardPriceDisplay.normalizePropertyKey(propertyId);
        if (normalized == null) {
            return null;
        }

        return SafeguardPricing.PROTECTION_PROPERTIES.stream()
                .filter(property -> SafeguardPricing.propertyKey(property).equals(normalized))
                .findFirst()
                .orElse(null);
    }

    private static final class ChunkCoordKeyClient {
        private static String encode(net.minecraft.resources.ResourceLocation dimension, int x, int z) {
            return dimension + "#" + x + "#" + z;
        }
    }
}
