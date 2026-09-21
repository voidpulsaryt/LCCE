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
 * Works out, for one team, the order billing should give up on things when funds run short
 * and the order it should restore them in when funds recover.
 *
 * <p>Entries come back sorted ascending by {@link PriorityEntry#priority()}: the first entry
 * is the cheapest protection toggle (dismantled last, restored first), and the list ends with
 * the priciest active outgoing war (the first thing dismantled when a team can't keep up).
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
        TeamQueuedChanges queuedChanges = savedData.getPendingState(team.getTeamId());

        List<PriorityEntry> orderedEntries = new ArrayList<>();
        int nextPriority = 1;

        nextPriority = appendProtectionEntries(server, team, queuedChanges, orderedEntries, nextPriority);
        appendOutgoingWarEntries(server, team, savedData, orderedEntries, nextPriority);

        return orderedEntries;
    }

    /** Appends one entry per billable protection setting, in restore order, starting at {@code startingPriority}. */
    private static int appendProtectionEntries(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges queuedChanges,
            List<PriorityEntry> orderedEntries,
            int startingPriority
    ) {
        int nextPriority = startingPriority;
        for (TeamProperty<?> property : SafeguardDismantleSequence.restoreOrder()) {
            if (!SafeguardRollbackService.isLiveProtectionBillable(team, property)) {
                continue;
            }
            String propertyKey = SafeguardPricing.propertyKey(property);
            orderedEntries.add(new PriorityEntry(
                    nextPriority++,
                    EntryKind.PROTECTION,
                    propertyKey,
                    protectionLabel(propertyKey),
                    protectionUpkeepCopper(server, team, queuedChanges, property)
            ));
        }
        return nextPriority;
    }

    /** Appends one entry per active outgoing war, cheapest to re-declare first. */
    private static void appendOutgoingWarEntries(
            MinecraftServer server,
            Team team,
            LcClaimEconomySavedData savedData,
            List<PriorityEntry> orderedEntries,
            int startingPriority
    ) {
        List<UUID> outgoingTargets = new ArrayList<>(savedData.getWarTargets(team.getTeamId()));
        outgoingTargets.sort(Comparator.comparingLong(targetId -> {
            Team target = TeamRegistry.resolve(server, targetId);
            return target == null ? 0L : ConflictService.costToDeclareWar(server, team, target);
        }));

        int nextPriority = startingPriority;
        for (UUID targetId : outgoingTargets) {
            Team target = TeamRegistry.resolve(server, targetId);
            if (target == null) {
                continue;
            }
            orderedEntries.add(new PriorityEntry(
                    nextPriority++,
                    EntryKind.OUTGOING_WAR,
                    targetId.toString(),
                    Component.literal(ConflictService.displayName(target)),
                    ConflictService.costToDeclareWar(server, team, target)
            ));
        }
    }

    private static Component protectionLabel(String propertyKey) {
        return Component.translatable("message.lc_claim_economy.upkeep_priority.protection." + propertyKey);
    }

    /** The copper this single property contributes to upkeep: total cost minus cost with it pinned at its cheapest setting. */
    private static long protectionUpkeepCopper(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges queuedChanges,
            TeamProperty<?> property
    ) {
        SafeguardPricing.ChunkCounts billableChunks = SafeguardPricing.countBillableChunks(server, team);
        Map<String, String> livePricing = SafeguardRollbackService.pricingProperties(team, queuedChanges);
        long costWithLiveSetting = SafeguardPricing.calculateProtectionCopper(team.getProperties(), livePricing, billableChunks);

        String propertyKey = SafeguardPricing.propertyKey(property);
        Map<String, String> pricingAtFloor = new HashMap<>(livePricing);
        pricingAtFloor.put(propertyKey, cheapestSerializedValue(property));

        long costAtFloor = SafeguardPricing.calculateProtectionCopper(team.getProperties(), pricingAtFloor, billableChunks);
        return Math.max(0L, costWithLiveSetting - costAtFloor);
    }

    private static String cheapestSerializedValue(TeamProperty<?> property) {
        if (property instanceof dev.ftb.mods.ftbteams.api.property.BooleanProperty) {
            return SafeguardPricing.serializePropertyValue(property, true);
        }
        return SafeguardPricing.serializePropertyValue(
                property,
                dev.ftb.mods.ftbteams.api.property.PrivacyMode.PUBLIC
        );
    }
}
