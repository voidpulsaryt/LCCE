package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyCollection;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.teams.LandProperties;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;

public record BillingBreakdown(
        UUID teamId,
        MoneyValue totalCost,
        int periodMinutes,
        int chunkCount,
        int buildBillableChunks,
        int buildUnits,
        int landBillableChunks,
        int landUnits,
        int forceLoadCount,
        long buildBasePrice,
        long landBasePrice,
        long buildProtectionCopper,
        long landProtectionCopper,
        long forceLoadCopper,
        long baseUpkeepCopper,
        long incomingWarCopper,
        long outgoingWarCopper,
        int incomingWarCount,
        int outgoingWarCount,
        List<ProtectionLine> buildProtectionLines,
        List<ProtectionLine> landProtectionLines,
        List<WarLine> warLines,
        List<PendingProtectionLine> pendingProtections,
        List<PendingWarLine> pendingWars,
        int pendingForceLoadCount,
        int pendingForceUnloadCount,
        int pendingLandChunkCount,
        int pendingBuildChunkCount
) {
    public record ProtectionLine(String labelKey, long pricePerChunk, String extraArg) {
        public ProtectionLine(String labelKey, long pricePerChunk) {
            this(labelKey, pricePerChunk, null);
        }
    }

    public record WarLine(String displayName, long warCostCopper, boolean incoming) {
    }

    public record PendingProtectionLine(String labelKey, String desiredValue, boolean dismantled) {
    }

    public record PendingWarLine(String displayName, boolean endWar) {
    }

    public static BillingBreakdown capture(
            MinecraftServer server,
            Team team,
            int forceLoadCount,
            MoneyValue totalCost,
            TeamQueuedChanges pendingState
    ) {
        int periodMinutes = LcClaimEconomyConfig.SERVER.upkeepPeriodMinutes.get();
        TeamPropertyCollection properties = team.getProperties();
        Map<String, String> noPending = Map.of();
        var chunkData = dev.ftb.mods.ftbchunks.api.FTBChunksAPI.api().getManager().getOrCreateData(team);

        SafeguardPricing.ChunkCounts counts = SafeguardPricing.countBillableChunks(server, team, chunkData, pendingState);
        ConflictService.WarCostBreakdown warCosts = ConflictService.calculateWarCosts(server, team);

        long buildBase = SafeguardPricing.calculateBuildBasePrice(properties, noPending);
        int buildUnits = counts.buildBillable();
        long buildProtectionCopper = scaledCopper(buildBase, buildUnits);

        long landBase = SafeguardPricing.calculateLandBasePrice(properties, noPending);
        int landUnits = SafeguardPricing.landChunkUnits(counts.landBillable());
        long landProtectionCopper = scaledCopper(landBase, landUnits);

        long forceLoadUnit = LcClaimEconomyConfig.SERVER.forceLoadUpkeepPrice.get();
        long forceLoadCopper = forceLoadCount > 0 ? scaledCopper(forceLoadUnit, forceLoadCount) : 0L;

        return new BillingBreakdown(
                team.getTeamId(),
                totalCost,
                periodMinutes,
                counts.totalChunks(),
                counts.buildBillable(),
                buildUnits,
                counts.landBillable(),
                landUnits,
                forceLoadCount,
                buildBase,
                landBase,
                buildProtectionCopper,
                landProtectionCopper,
                forceLoadCopper,
                warCosts.baseUpkeepCopper(),
                warCosts.incomingWarCopper(),
                warCosts.outgoingWarCopper(),
                warCosts.incomingWarCount(),
                warCosts.outgoingWarCount(),
                collectBuildProtectionLines(team),
                collectLandProtectionLines(team),
                collectWarLines(server, team),
                collectPendingProtections(team, pendingState),
                collectPendingWars(server, team, pendingState),
                pendingState.pendingForceLoads().size(),
                pendingState.pendingForceUnloads().size(),
                pendingState.pendingLandChunks().size(),
                pendingState.pendingBuildChunks().size()
        );
    }

    private static long scaledCopper(long unitPrice, int units) {
        return unitPrice > 0 ? unitPrice * units : 0L;
    }

    public boolean hasPendingItems() {
        return !pendingProtections.isEmpty()
                || !pendingWars.isEmpty()
                || pendingForceLoadCount > 0
                || pendingForceUnloadCount > 0
                || pendingLandChunkCount > 0
                || pendingBuildChunkCount > 0;
    }

    /** Concatenates the two directional view lists into one, tagging each with its direction as it maps across rather than appending into a shared accumulator. */
    private static List<WarLine> collectWarLines(MinecraftServer server, Team team) {
        return Stream.concat(
                ConflictService.buildBilledIncomingViews(server, team).stream()
                        .map(view -> new WarLine(view.displayName(), view.warCostCopper(), true)),
                ConflictService.buildBilledOutgoingViews(server, team).stream()
                        .map(view -> new WarLine(view.displayName(), view.warCostCopper(), false))
        ).toList();
    }

    /**
     * Each candidate protection line is expressed as a rule that either produces a line or
     * doesn't ({@code Optional}), evaluated in order and flattened - a declarative table
     * instead of six independent "append if applicable" statements.
     */
    private static List<ProtectionLine> collectBuildProtectionLines(Team team) {
        var config = LcClaimEconomyConfig.SERVER;
        List<Supplier<Optional<ProtectionLine>>> rules = List.of(
                () -> booleanLine(team, FTBChunksProperties.ALLOW_MOB_GRIEFING,
                        "message.lc_claim_economy.upkeep_detail.mob_grief", config.mobGriefProtectionPrice.get()),
                () -> booleanLine(team, FTBChunksProperties.ALLOW_EXPLOSIONS,
                        "message.lc_claim_economy.upkeep_detail.explosions", config.explosionProtectionPrice.get()),
                () -> booleanLine(team, FTBChunksProperties.ALLOW_PVP,
                        "message.lc_claim_economy.upkeep_detail.pvp", config.pvpDisablePrice.get()),
                () -> privacyLine(team.getProperty(FTBChunksProperties.BLOCK_INTERACT_MODE),
                        "message.lc_claim_economy.upkeep_detail.block_interact", config.blockInteractProtectionPrice.get()),
                () -> privacyLine(team.getProperty(FTBChunksProperties.BLOCK_EDIT_MODE),
                        "message.lc_claim_economy.upkeep_detail.block_edit", config.blockEditProtectionPrice.get()),
                () -> privacyLine(team.getProperty(FTBChunksProperties.ENTITY_INTERACT_MODE),
                        "message.lc_claim_economy.upkeep_detail.entity_interact", config.entityInteractProtectionPrice.get())
        );
        return rules.stream().map(Supplier::get).flatMap(Optional::stream).toList();
    }

    private static Optional<ProtectionLine> booleanLine(Team team, TeamProperty<Boolean> property, String labelKey, long price) {
        return team.getProperty(property) ? Optional.empty() : Optional.of(new ProtectionLine(labelKey, price));
    }

    private static List<ProtectionLine> collectLandProtectionLines(Team team) {
        var config = LcClaimEconomyConfig.SERVER;
        List<Supplier<Optional<ProtectionLine>>> rules = List.of(
                () -> privacyLine(team.getProperty(LandProperties.LAND_BLOCK_INTERACT_MODE),
                        "message.lc_claim_economy.upkeep_detail.block_interact", config.blockInteractProtectionPrice.get()),
                () -> privacyLine(team.getProperty(LandProperties.LAND_BLOCK_EDIT_MODE),
                        "message.lc_claim_economy.upkeep_detail.block_edit", config.blockEditProtectionPrice.get())
        );
        return rules.stream().map(Supplier::get).flatMap(Optional::stream).toList();
    }

    private static Optional<ProtectionLine> privacyLine(PrivacyMode mode, String labelKey, long price) {
        return mode == PrivacyMode.PUBLIC ? Optional.empty() : Optional.of(new ProtectionLine(labelKey, price, mode.name()));
    }

    private static List<PendingProtectionLine> collectPendingProtections(Team team, TeamQueuedChanges pendingState) {
        return SafeguardPricing.PROTECTION_PROPERTIES.stream()
                .map(property -> pendingProtectionLineFor(team, property, pendingState))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<PendingProtectionLine> pendingProtectionLineFor(Team team, TeamProperty<?> property, TeamQueuedChanges pendingState) {
        String key = SafeguardPricing.propertyKey(property);
        if (!pendingState.hasPendingProperty(key)) {
            return Optional.empty();
        }
        String desiredValue = formatPendingPropertyValue(property, pendingState.pendingProperties().get(key));
        String labelKey = "message.lc_claim_economy.upkeep_priority.protection." + key;
        if (SafeguardRollbackService.isDismantled(team, property, pendingState)) {
            return Optional.of(new PendingProtectionLine(labelKey, desiredValue, true));
        }
        if (SafeguardRollbackService.hasPendingApply(team, property, pendingState)) {
            return Optional.of(new PendingProtectionLine(labelKey, desiredValue, false));
        }
        return Optional.empty();
    }

    private static List<PendingWarLine> collectPendingWars(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState
    ) {
        return Stream.concat(
                pendingState.pendingWarDeclares().stream().map(id -> new PendingWarLine(resolveTeamName(server, id), false)),
                pendingState.pendingWarEnds().stream().map(id -> new PendingWarLine(resolveTeamName(server, id), true))
        ).toList();
    }

    private static String resolveTeamName(MinecraftServer server, UUID teamId) {
        Team team = TeamRegistry.resolve(server, teamId);
        return team != null ? ConflictService.displayName(team) : teamId.toString();
    }

    private static String formatPendingPropertyValue(TeamProperty<?> property, String serialized) {
        return switch (property) {
            case dev.ftb.mods.ftbteams.api.property.PrivacyProperty privacyProp ->
                    SafeguardPricing.deserializePropertyValue(privacyProp, serialized, PrivacyMode.PUBLIC).name();
            case dev.ftb.mods.ftbteams.api.property.BooleanProperty boolProp ->
                    String.valueOf(SafeguardPricing.deserializePropertyValue(boolProp, serialized, true));
            default -> serialized;
        };
    }

    public long forceLoadUnitPrice() {
        return LcClaimEconomyConfig.SERVER.forceLoadUpkeepPrice.get();
    }

    public long totalWarCopper() {
        return incomingWarCopper + outgoingWarCopper;
    }
}
