package dev.voidpulsar.lc_claim_economy.handler;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamPropertiesChangedEvent;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyCollection;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateBroadcast;
import dev.voidpulsar.lc_claim_economy.service.ClaimVisibilityRules;
import dev.voidpulsar.lc_claim_economy.service.SafeguardRollbackService;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPricing;
import dev.voidpulsar.lc_claim_economy.service.SafeguardEnforcementService;
import dev.voidpulsar.lc_claim_economy.service.ConflictSyncCoordinator;
import dev.voidpulsar.lc_claim_economy.teams.LandProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class TeamPropertyHandler {
    public TeamPropertyHandler() {
        TeamEvent.PROPERTIES_CHANGED.register(this::onTeamPropertiesChanged);
    }

    private void onTeamPropertiesChanged(TeamPropertiesChangedEvent event) {
        if (SafeguardEnforcementService.isReverting() || SafeguardEnforcementService.isApplying()) {
            LcClaimEconomy.LOGGER.info("[PendingDebug] PROPERTIES_CHANGED ignored (reverting={}, applying={})",
                    SafeguardEnforcementService.isReverting(), SafeguardEnforcementService.isApplying());
            return;
        }

        Team team = event.getTeam();
        if (!team.isValid()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        TeamPropertyCollection previous = event.getPreviousProperties();
        TeamQueuedChanges pendingState = savedData.getPendingState(team.getTeamId());
        SafeguardPricing.ChunkCounts counts = SafeguardPricing.countBillableChunks(server, team);

        SafeguardEnforcementService.tryUnlock(server, team);

        if (hasPropertyChanged(previous, team, FTBChunksProperties.CLAIM_VISIBILITY)
                && team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY) != PrivacyMode.PUBLIC) {
            LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: claim_visibility change to {} rejected (always public)",
                    team.getShortName(), team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY));
            SafeguardEnforcementService.runReverting(() -> {
                team.setProperty(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
                team.syncOnePropertyToTeam(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
            });
            notifyTeam(team, "message.lc_claim_economy.claim_visibility_locked");
        }

        for (TeamProperty<?> property : SafeguardPricing.PROTECTION_PROPERTIES) {
            String key = SafeguardPricing.propertyKey(property);

            if (!hasPropertyChanged(previous, team, property)) {
                // The submitted value matches the live server value. Untouched
                // entries never land here while a change is queued, because the
                // client pre-fills queued values into the screen (so they come
                // back as a "change" matching the pending value). Ending up
                // here with a queued change means the player switched the
                // setting back, which makes the queued change obsolete.
                if (pendingState.pendingProperties().containsKey(key)) {
                    pendingState = pendingState.withoutPendingProperty(key);
                    savedData.setPendingState(team.getTeamId(), pendingState);
                    LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: {} switched back to current value, pending cancelled; pending now: {}",
                            team.getShortName(), key, pendingState.pendingProperties());
                    notifyTeam(team, "message.lc_claim_economy.protection_pending_cancelled");
                }
                continue;
            }

            Object previousValue = previous.get(property);
            Object newValue = team.getProperty(property);
            LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: {} changed {} -> {}",
                    team.getShortName(), key, previousValue, newValue);

            if (SafeguardRollbackService.isDismantled(team, property, pendingState)) {
                pendingState = handleDismantledPropertyChange(server, team, property, pendingState, savedData, newValue);
                continue;
            }

            String serializedNew = SafeguardPricing.serializePropertyValue(property, newValue);
            String existingPending = pendingState.pendingProperties().get(key);

            if (existingPending != null) {
                if (existingPending.equals(serializedNew)) {
                    // Matches already-queued value — revert live display, keep pending.
                    LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: {} matches queued value {}, keeping pending",
                            team.getShortName(), key, existingPending);
                    revertProperty(server, team, previous, property);
                    continue;
                }

                // Replace queued value. Cost-neutral vs the active value → apply immediately.
                TeamQueuedChanges droppedState = pendingState.withoutPendingProperty(key);
                long activePrice = SafeguardPricing.calculateProtectionCopper(previous, droppedState.pendingProperties(), counts);
                long submittedPrice = SafeguardPricing.calculateProtectionCopper(
                        previous, droppedState.withPendingProperty(key, serializedNew).pendingProperties(), counts);
                if (activePrice == submittedPrice) {
                    pendingState = droppedState;
                    savedData.setPendingState(team.getTeamId(), pendingState);
                    LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: {} cost-neutral vs active, applied immediately; pending now: {}",
                            team.getShortName(), key, pendingState.pendingProperties());
                    continue;
                }

                pendingState = pendingState.withPendingProperty(key, serializedNew);
                savedData.setPendingState(team.getTeamId(), pendingState);
                revertProperty(server, team, previous, property);
                LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: {} pending replaced {} -> {}; pending now: {}",
                        team.getShortName(), key, existingPending, serializedNew, pendingState.pendingProperties());
                notifyProtectionPending(team);
                continue;
            }

            // Prices must be calculated from the PREVIOUS property collection:
            // when this event fires the team already carries the new values,
            // so using the team would always yield oldPrice == newPrice.
            TeamQueuedChanges simulatedState = pendingState.withPendingProperty(key, serializedNew);
            long oldPrice = SafeguardPricing.calculateProtectionCopper(previous, pendingState.pendingProperties(), counts);
            long newPrice = SafeguardPricing.calculateProtectionCopper(previous, simulatedState.pendingProperties(), counts);
            LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: {} base price {} -> {}",
                    team.getShortName(), key, oldPrice, newPrice);

            if (oldPrice == newPrice && !shouldQueueProtectionPendingWhenTotalUnchanged(
                    property, previous, pendingState, simulatedState)) {
                // Cost-neutral change — apply immediately without queuing.
                savedData.setPendingState(team.getTeamId(), pendingState);
                LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: {} applied immediately (price unchanged)",
                        team.getShortName(), key);
                continue;
            }

            // Cost-increasing or base-price-changing: queue for next upkeep period.
            // No affordability check — the upkeep tick handles payment; if the
            // team cannot afford it the change stays pending until they can.
            pendingState = simulatedState;
            savedData.setPendingState(team.getTeamId(), pendingState);
            revertProperty(server, team, previous, property);
            LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: {} queued as pending; pending now: {}",
                    team.getShortName(), key, pendingState.pendingProperties());
            notifyProtectionPending(team);
        }

        QueuedStateBroadcast.syncTeam(server, team);
        ConflictSyncCoordinator.onUpkeepFactorsChanged(server, team);
    }

    private static TeamQueuedChanges handleDismantledPropertyChange(
            MinecraftServer server,
            Team team,
            TeamProperty<?> property,
            TeamQueuedChanges pendingState,
            LcClaimEconomySavedData savedData,
            Object newValue
    ) {
        String key = SafeguardPricing.propertyKey(property);
        String serializedNew = SafeguardPricing.serializePropertyValue(property, newValue);
        SafeguardEnforcementService.runReverting(() -> SafeguardRollbackService.revertLiveToMinimum(team, property));

        TeamQueuedChanges updated;
        if (SafeguardRollbackService.isSerializedMinimum(property, serializedNew)) {
            updated = pendingState.withoutPendingProperty(key);
            if (pendingState.hasPendingProperty(key)) {
                notifyTeam(team, "message.lc_claim_economy.protection_pending_cancelled");
            }
        } else {
            updated = pendingState.withPendingProperty(key, serializedNew);
            notifyProtectionPending(team);
        }
        savedData.setPendingState(team.getTeamId(), updated);
        LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: dismantled {} pending updated to {}; pending now: {}",
                team.getShortName(), key, serializedNew, updated.pendingProperties());
        return updated;
    }

    /**
     * Protection changes can alter the per-chunk base price while total upkeep
     * stays flat (no billable chunks yet, or only free chunks). Queue those
     * changes so they apply at the next upkeep period and the UI shows pending.
     */
    private static boolean shouldQueueProtectionPendingWhenTotalUnchanged(
            TeamProperty<?> property,
            TeamPropertyCollection previous,
            TeamQueuedChanges pendingState,
            TeamQueuedChanges simulatedState
    ) {
        if (LandProperties.isLandProperty(property)) {
            long oldBase = SafeguardPricing.calculateLandBasePrice(previous, pendingState.pendingProperties());
            long newBase = SafeguardPricing.calculateLandBasePrice(previous, simulatedState.pendingProperties());
            return oldBase != newBase;
        }
        long oldBase = SafeguardPricing.calculateBuildBasePrice(previous, pendingState.pendingProperties());
        long newBase = SafeguardPricing.calculateBuildBasePrice(previous, simulatedState.pendingProperties());
        return oldBase != newBase;
    }

    private static <T> boolean hasPropertyChanged(TeamPropertyCollection previous, Team team, TeamProperty<T> property) {
        return !previous.get(property).equals(team.getProperty(property));
    }

    private static <T> void revertProperty(MinecraftServer server, Team team, TeamPropertyCollection previous, TeamProperty<T> property) {
        T value = previous.get(property);
        SafeguardEnforcementService.runReverting(() -> {
            team.setProperty(property, value);
            // setProperty alone does not sync to clients, and
            // syncOnePropertyToAll is a no-op for protection properties (not
            // flagged shouldSyncToAll). syncOnePropertyToTeam reliably pushes
            // the reverted (active) value to all online team members so the
            // client cache always matches the server's active value.
            team.syncOnePropertyToTeam(property, value);
        });
        LcClaimEconomy.LOGGER.info("[PendingDebug] Team {}: reverted {} to {} (synced to team)",
                team.getShortName(), SafeguardPricing.propertyKey(property), value);
    }

    private static void notifyProtectionPending(Team team) {
        notifyTeam(team, "message.lc_claim_economy.protection_change_pending");
    }

    private static void notifyTeam(Team team, String messageKey) {
        Component message = Component.translatable(messageKey);
        for (ServerPlayer member : team.getOnlineMembers()) {
            member.displayClientMessage(message, false);
        }
    }
}
