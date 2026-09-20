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
import java.util.UUID;

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
        long buildProtectionCopper = buildBase > 0 ? buildBase * buildUnits : 0L;

        long landBase = SafeguardPricing.calculateLandBasePrice(properties, noPending);
        int landUnits = SafeguardPricing.landChunkUnits(counts.landBillable());
        long landProtectionCopper = landBase > 0 ? landBase * landUnits : 0L;

        long forceLoadUnit = LcClaimEconomyConfig.SERVER.forceLoadUpkeepPrice.get();
        long forceLoadCopper = forceLoadUnit > 0 && forceLoadCount > 0 ? forceLoadUnit * forceLoadCount : 0L;

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

    public boolean hasPendingItems() {
        return !pendingProtections.isEmpty()
                || !pendingWars.isEmpty()
                || pendingForceLoadCount > 0
                || pendingForceUnloadCount > 0
                || pendingLandChunkCount > 0
                || pendingBuildChunkCount > 0;
    }

    private static List<WarLine> collectWarLines(MinecraftServer server, Team team) {
        List<WarLine> lines = new ArrayList<>();
        for (ConflictService.WarTeamView view : ConflictService.buildBilledIncomingViews(server, team)) {
            lines.add(new WarLine(view.displayName(), view.warCostCopper(), true));
        }
        for (ConflictService.WarTeamView view : ConflictService.buildBilledOutgoingViews(server, team)) {
            lines.add(new WarLine(view.displayName(), view.warCostCopper(), false));
        }
        return lines;
    }

    private static List<ProtectionLine> collectBuildProtectionLines(Team team) {
        List<ProtectionLine> lines = new ArrayList<>();
        var config = LcClaimEconomyConfig.SERVER;

        if (!team.getProperty(FTBChunksProperties.ALLOW_MOB_GRIEFING)) {
            lines.add(new ProtectionLine("message.lc_claim_economy.upkeep_detail.mob_grief", config.mobGriefProtectionPrice.get()));
        }
        if (!team.getProperty(FTBChunksProperties.ALLOW_EXPLOSIONS)) {
            lines.add(new ProtectionLine("message.lc_claim_economy.upkeep_detail.explosions", config.explosionProtectionPrice.get()));
        }
        if (!team.getProperty(FTBChunksProperties.ALLOW_PVP)) {
            lines.add(new ProtectionLine("message.lc_claim_economy.upkeep_detail.pvp", config.pvpDisablePrice.get()));
        }
        addPrivacyLine(lines, team.getProperty(FTBChunksProperties.BLOCK_INTERACT_MODE),
                "message.lc_claim_economy.upkeep_detail.block_interact", config.blockInteractProtectionPrice.get());
        addPrivacyLine(lines, team.getProperty(FTBChunksProperties.BLOCK_EDIT_MODE),
                "message.lc_claim_economy.upkeep_detail.block_edit", config.blockEditProtectionPrice.get());
        addPrivacyLine(lines, team.getProperty(FTBChunksProperties.ENTITY_INTERACT_MODE),
                "message.lc_claim_economy.upkeep_detail.entity_interact", config.entityInteractProtectionPrice.get());
        return lines;
    }

    private static List<ProtectionLine> collectLandProtectionLines(Team team) {
        List<ProtectionLine> lines = new ArrayList<>();
        var config = LcClaimEconomyConfig.SERVER;

        addPrivacyLine(lines, team.getProperty(LandProperties.LAND_BLOCK_INTERACT_MODE),
                "message.lc_claim_economy.upkeep_detail.block_interact", config.blockInteractProtectionPrice.get());
        addPrivacyLine(lines, team.getProperty(LandProperties.LAND_BLOCK_EDIT_MODE),
                "message.lc_claim_economy.upkeep_detail.block_edit", config.blockEditProtectionPrice.get());
        return lines;
    }

    private static void addPrivacyLine(List<ProtectionLine> lines, PrivacyMode mode, String labelKey, long price) {
        if (mode != PrivacyMode.PUBLIC) {
            lines.add(new ProtectionLine(labelKey, price, mode.name()));
        }
    }

    private static List<PendingProtectionLine> collectPendingProtections(Team team, TeamQueuedChanges pendingState) {
        List<PendingProtectionLine> lines = new ArrayList<>();
        for (TeamProperty<?> property : SafeguardPricing.PROTECTION_PROPERTIES) {
            String key = SafeguardPricing.propertyKey(property);
            if (!pendingState.hasPendingProperty(key)) {
                continue;
            }
            String desiredValue = formatPendingPropertyValue(property, pendingState.pendingProperties().get(key));
            String labelKey = "message.lc_claim_economy.upkeep_priority.protection." + key;
            if (SafeguardRollbackService.isDismantled(team, property, pendingState)) {
                lines.add(new PendingProtectionLine(labelKey, desiredValue, true));
            } else if (SafeguardRollbackService.hasPendingApply(team, property, pendingState)) {
                lines.add(new PendingProtectionLine(labelKey, desiredValue, false));
            }
        }
        return lines;
    }

    private static List<PendingWarLine> collectPendingWars(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState
    ) {
        List<PendingWarLine> lines = new ArrayList<>();
        for (UUID targetId : pendingState.pendingWarDeclares()) {
            lines.add(new PendingWarLine(resolveTeamName(server, targetId), false));
        }
        for (UUID targetId : pendingState.pendingWarEnds()) {
            lines.add(new PendingWarLine(resolveTeamName(server, targetId), true));
        }
        return lines;
    }

    private static String resolveTeamName(MinecraftServer server, UUID teamId) {
        Team team = TeamRegistry.resolve(server, teamId);
        return team != null ? ConflictService.displayName(team) : teamId.toString();
    }

    private static String formatPendingPropertyValue(TeamProperty<?> property, String serialized) {
        if (property instanceof dev.ftb.mods.ftbteams.api.property.PrivacyProperty privacyProp) {
            PrivacyMode mode = SafeguardPricing.deserializePropertyValue(
                    privacyProp,
                    serialized,
                    PrivacyMode.PUBLIC
            );
            return mode.name();
        }
        if (property instanceof dev.ftb.mods.ftbteams.api.property.BooleanProperty boolProp) {
            boolean value = SafeguardPricing.deserializePropertyValue(boolProp, serialized, true);
            return String.valueOf(value);
        }
        return serialized;
    }

    public long forceLoadUnitPrice() {
        return LcClaimEconomyConfig.SERVER.forceLoadUpkeepPrice.get();
    }

    public long totalWarCopper() {
        return incomingWarCopper + outgoingWarCopper;
    }
}
