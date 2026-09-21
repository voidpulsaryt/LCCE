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
     * Billable chunk counts, split by type. The free allowance is spent on build
     * chunks first; only what's left over after that reduces the land chunk count -
     * so a team with more claims than free chunks but still within the allowance on
     * build alone pays nothing extra for land, and vice versa.
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
        int totalChunks = chunkData.getClaimedChunks().size();
        int landChunks = ProtectionEnforcement.countEffectiveLandChunks(server, team, chunkData, pendingState);
        int buildChunks = Math.max(0, totalChunks - landChunks);

        int freeAllowance = ComplimentaryChunkAllotment.allowance();
        int buildBillable = Math.max(0, buildChunks - freeAllowance);
        int allowanceRemainingForLand = Math.max(0, freeAllowance - buildChunks);
        int landBillable = Math.max(0, landChunks - allowanceRemainingForLand);
        return new ChunkCounts(totalChunks, buildBillable, landBillable);
    }

    /**
     * Land is charged per started group rather than per chunk: the billable land
     * count rounds up to the next full {@code landChunkGroupSize}-chunk group before
     * dividing, so e.g. one chunk over a group boundary still costs a whole extra
     * unit - this is what makes land cheaper per-chunk than build territory.
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
        var config = LcClaimEconomyConfig.SERVER;
        long total = 0L;
        if (!getBooleanProperty(properties, FTBChunksProperties.ALLOW_MOB_GRIEFING, pendingProperties)) {
            total += config.mobGriefProtectionPrice.get();
        }
        if (!getBooleanProperty(properties, FTBChunksProperties.ALLOW_EXPLOSIONS, pendingProperties)) {
            total += config.explosionProtectionPrice.get();
        }
        if (!getBooleanProperty(properties, FTBChunksProperties.ALLOW_PVP, pendingProperties)) {
            total += config.pvpDisablePrice.get();
        }
        if (getPrivacyProperty(properties, FTBChunksProperties.BLOCK_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            total += config.blockInteractProtectionPrice.get();
        }
        if (getPrivacyProperty(properties, FTBChunksProperties.BLOCK_EDIT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            total += config.blockEditProtectionPrice.get();
        }
        if (getPrivacyProperty(properties, FTBChunksProperties.ENTITY_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            total += config.entityInteractProtectionPrice.get();
        }
        return total;
    }

    /** Land chunks only ever expose the two block-privacy settings - there's no mob-griefing/explosion/PvP toggle for them. */
    public static long calculateLandBasePrice(TeamPropertyCollection properties, Map<String, String> pendingProperties) {
        var config = LcClaimEconomyConfig.SERVER;
        long total = 0L;
        if (getPrivacyProperty(properties, LandProperties.LAND_BLOCK_INTERACT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            total += config.blockInteractProtectionPrice.get();
        }
        if (getPrivacyProperty(properties, LandProperties.LAND_BLOCK_EDIT_MODE, pendingProperties) != PrivacyMode.PUBLIC) {
            total += config.blockEditProtectionPrice.get();
        }
        return total;
    }

    /**
     * Combines both chunk types into one upkeep figure: build chunks are charged
     * per-chunk at the build rate, while land chunks are charged per started group
     * (see {@link #landChunkUnits(int)}) at the land rate.
     */
    public static long calculateProtectionCopper(
            TeamPropertyCollection properties,
            Map<String, String> pendingProperties,
            ChunkCounts counts
    ) {
        long buildBase = calculateBuildBasePrice(properties, pendingProperties);
        long buildCopper = (buildBase > 0 && counts.buildBillable() > 0) ? buildBase * counts.buildBillable() : 0L;

        long landBase = calculateLandBasePrice(properties, pendingProperties);
        long landCopper = (landBase > 0 && counts.landBillable() > 0) ? landBase * landChunkUnits(counts.landBillable()) : 0L;

        return buildCopper + landCopper;
    }

    public static long calculateForceLoadCopper(int forceLoadCount) {
        long unitPrice = LcClaimEconomyConfig.SERVER.forceLoadUpkeepPrice.get();
        return (unitPrice > 0 && forceLoadCount > 0) ? unitPrice * forceLoadCount : 0L;
    }

    public static MoneyValue calculateTotalUpkeepCost(MinecraftServer server, Team team, TeamQueuedChanges pendingState) {
        return CurrencyAmounts.fromCopper(calculateTotalUpkeepCopper(server, team, pendingState));
    }

    public static long calculateTotalUpkeepCopper(MinecraftServer server, Team team, TeamQueuedChanges pendingState) {
        Map<String, String> livePricing = SafeguardRollbackService.pricingProperties(team, pendingState);
        return calculateTotalUpkeepCopper(server, team, pendingState, livePricing);
    }

    public static long calculateTotalUpkeepCopper(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            Map<String, String> pricingOverrides
    ) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        ChunkCounts counts = countBillableChunks(server, team, chunkData, pendingState);
        long protectionCopper = calculateProtectionCopper(team.getProperties(), pricingOverrides, counts);
        long forceLoadCopper = calculateForceLoadCopper(countEffectiveForceLoads(chunkData, pendingState));
        return protectionCopper + forceLoadCopper;
    }

    /** Live force-loaded chunk count adjusted for changes that are queued but not applied yet. */
    public static int countEffectiveForceLoads(ChunkTeamData chunkData, TeamQueuedChanges pendingState) {
        int projected = chunkData.getForceLoadedChunks().size()
                + pendingState.pendingForceLoads().size()
                - pendingState.pendingForceUnloads().size();
        return Math.max(projected, 0);
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
        Map<String, String> updated = new HashMap<>(pendingProperties);
        updated.put(propertyKey(property), serializePropertyValue(property, value));
        return updated;
    }

    /** Reads a boolean property, preferring a pending override over the live team value when one is queued. */
    private static boolean getBooleanProperty(
            TeamPropertyCollection properties,
            TeamProperty<Boolean> property,
            Map<String, String> pendingProperties
    ) {
        String key = propertyKey(property);
        String overrideValue = pendingProperties.get(key);
        return overrideValue != null
                ? deserializePropertyValue(property, overrideValue, properties.get(property))
                : properties.get(property);
    }

    /** Reads a privacy-mode property, preferring a pending override over the live team value when one is queued. */
    private static PrivacyMode getPrivacyProperty(
            TeamPropertyCollection properties,
            TeamProperty<PrivacyMode> property,
            Map<String, String> pendingProperties
    ) {
        String key = propertyKey(property);
        String overrideValue = pendingProperties.get(key);
        return overrideValue != null
                ? deserializePropertyValue(property, overrideValue, properties.get(property))
                : properties.get(property);
    }
}
