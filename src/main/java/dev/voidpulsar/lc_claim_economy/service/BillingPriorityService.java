package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the dismantle/restore priority list for a team. Priority 1 is the
 * protection dismantled last (restored first); the highest number is the most
 * expensive active outgoing war (dismantled first).
 */
public final class BillingPriorityService {
    public record PriorityEntry(
            int priority,
            EntryKind kind,
            String id,
            Component label,
            long costCopper
    ) {
    }

    public enum EntryKind {
        PROTECTION,
        OUTGOING_WAR
    }

    private BillingPriorityService() {
    }

    public static List<PriorityEntry> buildOrder(MinecraftServer server, Team team) {
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        TeamQueuedChanges pendingState = savedData.getPendingState(team.getTeamId());
        UUID teamId = team.getTeamId();
        List<PriorityEntry> entries = new ArrayList<>();
        int priority = 1;

        for (TeamProperty<?> property : SafeguardDismantleSequence.restoreOrder()) {
            if (!SafeguardRollbackService.isLiveProtectionBillable(team, property)) {
                continue;
            }
            String key = SafeguardPricing.propertyKey(property);
            entries.add(new PriorityEntry(
                    priority++,
                    EntryKind.PROTECTION,
                    key,
                    protectionLabel(key),
                    protectionUpkeepCopper(server, team, pendingState, property)
            ));
        }

        List<UUID> outgoing = new ArrayList<>(savedData.getWarTargets(teamId));
        outgoing.sort(Comparator.comparingLong(targetId -> {
            Team target = TeamRegistry.resolve(server, targetId);
            return target == null ? 0L : ConflictService.costToDeclareWar(server, team, target);
        }));

        for (UUID targetId : outgoing) {
            Team target = TeamRegistry.resolve(server, targetId);
            if (target == null) {
                continue;
            }
            entries.add(new PriorityEntry(
                    priority++,
                    EntryKind.OUTGOING_WAR,
                    targetId.toString(),
                    Component.literal(ConflictService.displayName(target)),
                    ConflictService.costToDeclareWar(server, team, target)
            ));
        }

        return entries;
    }

    private static Component protectionLabel(String key) {
        return Component.translatable("message.lc_claim_economy.upkeep_priority.protection." + key);
    }

    private static long protectionUpkeepCopper(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            TeamProperty<?> property
    ) {
        SafeguardPricing.ChunkCounts counts = SafeguardPricing.countBillableChunks(server, team);
        Map<String, String> pricing = SafeguardRollbackService.pricingProperties(team, pendingState);
        long withLive = SafeguardPricing.calculateProtectionCopper(team.getProperties(), pricing, counts);

        String key = SafeguardPricing.propertyKey(property);
        Map<String, String> atMinimum = new HashMap<>(pricing);
        atMinimum.put(key, minimumSerialized(property));

        long atMin = SafeguardPricing.calculateProtectionCopper(team.getProperties(), atMinimum, counts);
        return Math.max(0L, withLive - atMin);
    }

    private static String minimumSerialized(TeamProperty<?> property) {
        if (property instanceof dev.ftb.mods.ftbteams.api.property.BooleanProperty) {
            return SafeguardPricing.serializePropertyValue(property, true);
        }
        return SafeguardPricing.serializePropertyValue(
                property,
                dev.ftb.mods.ftbteams.api.property.PrivacyMode.PUBLIC
        );
    }
}
