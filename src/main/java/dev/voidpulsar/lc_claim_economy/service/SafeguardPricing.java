package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyCollection;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.teams.LandProperties;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SafeguardPricing {
    public static final Set<TeamProperty<?>> BUILD_PROTECTION_PROPERTIES = Set.of(
            FTBChunksProperties.ALLOW_MOB_GRIEFING,
            FTBChunksProperties.ALLOW_EXPLOSIONS,
            FTBChunksProperties.ALLOW_PVP,
            FTBChunksProperties.BLOCK_INTERACT_MODE,
            FTBChunksProperties.BLOCK_EDIT_MODE,
            FTBChunksProperties.ENTITY_INTERACT_MODE
    );

    /** All priced protection properties: build settings plus their land counterparts. */
    public static final Set<TeamProperty<?>> PROTECTION_PROPERTIES = combinedProperties();

    private static Set<TeamProperty<?>> combinedProperties() {
        Set<TeamProperty<?>> all = new LinkedHashSet<>(BUILD_PROTECTION_PROPERTIES);
        all.addAll(LandProperties.ALL);
        return Set.copyOf(all);
    }

    private SafeguardPricing() {
    }

    /**
     * Billable chunk counts per type. The free chunk allowance is consumed by
     * build chunks first, any remainder reduces the land chunk count.
     */
    public record ChunkCounts(int totalChunks, int buildBillable, int landBillable) {
        public static final ChunkCounts EMPTY = new ChunkCounts(0, 0, 0);
    }

    public static ChunkCounts countBillableChunks(MinecraftServer server, Team team) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return ChunkCounts.EMPTY;
        }
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        return countBillableChunks(server, team, chunkData);
    }

    public static ChunkCounts countBillableChunks(MinecraftServer server, Team team, ChunkTeamData chunkData) {
        return countBillableChunks(server, team, chunkData, new TeamQueuedChanges());
    }

    public static ChunkCounts countBillableChunks(
            MinecraftServer server,
            Team team,
            ChunkTeamData chunkData,
            TeamQueuedChanges pendingState
    ) {
        int total = chunkData.getClaimedChunks().size();
        int land = ProtectionEnforcement.countEffectiveLandChunks(server, team, chunkData, pendingState);
        int build = Math.max(0, total - land);

        int allowance = ComplimentaryChunkAllotment.allowance();
        int buildBillable = Math.max(0, build - allowance);
        int allowanceLeft = Math.max(0, allowance - build);
        int landBillable = Math.max(0, land - allowanceLeft);
        return new ChunkCounts(total, buildBillable, landBillable);
    }

    /**
     * Number of billed land price units: the billable land chunk count is
     * rounded up to the next full group of {@code landChunkGroupSize} chunks,
     * then divided by the group size (i.e. one charge per started group).
     */
    public static int landChunkUnits(int landBillable) {
        return landChunkUnits(landBillable, landChunkGroupSize());
    }

    static int landChunkUnits(int landBillable, int groupSize) {
        if (landBillable <= 0) {
            return 0;
        }
        groupSize = Math.max(1, groupSize);
        return (landBillable + groupSize - 1) / groupSize;
    }

    public static int landChunkGroupSize() {
        return Math.max(1, LcClaimEconomyConfig.SERVER.landChunkGroupSize.get());
    }

    public static long calculateBuildBasePrice(TeamPropertyCollection properties, Map<String, String> pendingProperties) {
        long base = 0L;
        if (!getBooleanProperty(properties, FTBChunksProperties.ALLOW_MOB_GRIEFING, pendingProperties)) {
            base += LcClaimEconomyConfig.SERVER.mobGriefProtectionPrice.get();
        }
        if (!getBooleanProperty(properties, FTBChunksProperties.ALLOW_EXPLOSIONS, pendingProperties)) {
            base += LcClaimEconomyConfig.SERVER.explosionProtectionPrice.get();
        }
        if (!getBooleanProperty(properties, FTBChunksProperties.ALLOW_PVP, pendingProperties)) {
            base += LcClaimEconomyConfig.SERVER.pvpDisablePrice.get();
        }
        if (getPrivacyProperty(properties, FTBChunksProperties.BLOCK_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LcClaimEconomyConfig.SERVER.blockInteractProtectionPrice.get();
        }
        if (getPrivacyProperty(properties, FTBChunksProperties.BLOCK_EDIT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LcClaimEconomyConfig.SERVER.blockEditProtectionPrice.get();
        }
        if (getPrivacyProperty(properties, FTBChunksProperties.ENTITY_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LcClaimEconomyConfig.SERVER.entityInteractProtectionPrice.get();
        }
        return base;
    }

    public static long calculateLandBasePrice(TeamPropertyCollection properties, Map<String, String> pendingProperties) {
        long base = 0L;
        // Land chunks can only be protected against block interaction/editing.
        if (getPrivacyProperty(properties, LandProperties.LAND_BLOCK_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LcClaimEconomyConfig.SERVER.blockInteractProtectionPrice.get();
        }
        if (getPrivacyProperty(properties, LandProperties.LAND_BLOCK_EDIT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            base += LcClaimEconomyConfig.SERVER.blockEditProtectionPrice.get();
        }
        return base;
    }

    /**
     * Protection upkeep in copper: build chunks pay the build base price per
     * chunk, land chunks (state territory) pay the land base price once per
     * group of {@code landChunkGroupSize} chunks (rounded up to the next full
     * group), which makes land cheaper to protect.
     */
    public static long calculateProtectionCopper(
            TeamPropertyCollection properties,
            Map<String, String> pendingProperties,
            ChunkCounts counts
    ) {
        long copper = 0L;
        long buildBase = calculateBuildBasePrice(properties, pendingProperties);
        if (buildBase > 0 && counts.buildBillable() > 0) {
            copper += buildBase * counts.buildBillable();
        }
        long landBase = calculateLandBasePrice(properties, pendingProperties);
        if (landBase > 0 && counts.landBillable() > 0) {
            copper += landBase * landChunkUnits(counts.landBillable());
        }
        return copper;
    }

    public static long calculateForceLoadCopper(int forceLoadCount) {
        long forceLoadPrice = LcClaimEconomyConfig.SERVER.forceLoadUpkeepPrice.get();
        if (forceLoadPrice > 0 && forceLoadCount > 0) {
            return forceLoadPrice * forceLoadCount;
        }
        return 0L;
    }

    public static MoneyValue calculateTotalUpkeepCost(MinecraftServer server, Team team, TeamQueuedChanges pendingState) {
        return CurrencyAmounts.fromCopper(calculateTotalUpkeepCopper(server, team, pendingState));
    }

    public static long calculateTotalUpkeepCopper(MinecraftServer server, Team team, TeamQueuedChanges pendingState) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        ChunkCounts counts = countBillableChunks(server, team, chunkData, pendingState);
        int forceLoadCount = countEffectiveForceLoads(chunkData, pendingState);
        long protectionCopper = calculateProtectionCopper(
                team.getProperties(),
                SafeguardRollbackService.pricingProperties(team, pendingState),
                counts
        );
        return protectionCopper + calculateForceLoadCopper(forceLoadCount);
    }

    public static long calculateTotalUpkeepCopper(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            Map<String, String> pricingOverrides
    ) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        ChunkCounts counts = countBillableChunks(server, team, chunkData, pendingState);
        int forceLoadCount = countEffectiveForceLoads(chunkData, pendingState);
        long protectionCopper = calculateProtectionCopper(team.getProperties(), pricingOverrides, counts);
        return protectionCopper + calculateForceLoadCopper(forceLoadCount);
    }

    public static int countEffectiveForceLoads(ChunkTeamData chunkData, TeamQueuedChanges pendingState) {
        int count = chunkData.getForceLoadedChunks().size();
        count += pendingState.pendingForceLoads().size();
        count -= pendingState.pendingForceUnloads().size();
        return Math.max(count, 0);
    }

    public static boolean isProtectionProperty(TeamProperty<?> property) {
        return PROTECTION_PROPERTIES.contains(property);
    }

    public static String propertyKey(TeamProperty<?> property) {
        return property.getId().getPath();
    }

    @SuppressWarnings("unchecked")
    public static String serializePropertyValue(TeamProperty<?> property, Object value) {
        return ((TeamProperty<Object>) property).toString(value);
    }

    public static <T> T deserializePropertyValue(TeamProperty<T> property, String value, T fallback) {
        return property.fromString(value).orElse(fallback);
    }

    public static Map<String, String> withPendingProperty(
            Map<String, String> pendingProperties,
            TeamProperty<?> property,
            Object value
    ) {
        Map<String, String> copy = new HashMap<>(pendingProperties);
        copy.put(propertyKey(property), serializePropertyValue(property, value));
        return copy;
    }

    private static boolean getBooleanProperty(
            TeamPropertyCollection properties,
            TeamProperty<Boolean> property,
            Map<String, String> pendingProperties
    ) {
        String key = propertyKey(property);
        if (pendingProperties.containsKey(key)) {
            return deserializePropertyValue(property, pendingProperties.get(key), properties.get(property));
        }
        return properties.get(property);
    }

    private static PrivacyMode getPrivacyProperty(
            TeamPropertyCollection properties,
            TeamProperty<PrivacyMode> property,
            Map<String, String> pendingProperties
    ) {
        String key = propertyKey(property);
        if (pendingProperties.containsKey(key)) {
            return deserializePropertyValue(property, pendingProperties.get(key), properties.get(property));
        }
        return properties.get(property);
    }
}
