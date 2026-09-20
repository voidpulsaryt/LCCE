package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import net.minecraft.network.chat.Component;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateBroadcast;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Upkeep settlement: restore protections before wars; dismantle wars before
 * protections. Queued and paused protections share pendingProperties and follow
 * the configured dismantle order (land first off, land last on).
 */
public final class BillingSettlementService {
    public record SettlementResult(
            boolean paid,
            MoneyValue charged,
            TeamQueuedChanges pendingState,
            int forceLoadCount,
            List<TeamProperty<?>> suspendedProtections,
            boolean warsSuspended,
            List<TeamProperty<?>> restoredProtections,
            List<String> restoredWarNames,
            List<TeamProperty<?>> unaffordableRestorations
    ) {
        public static SettlementResult skipped() {
            return new SettlementResult(false, MoneyValue.empty(), new TeamQueuedChanges(), 0,
                    List.of(), false, List.of(), List.of(), List.of());
        }

        public boolean anythingSuspended() {
            return !suspendedProtections.isEmpty() || warsSuspended;
        }

        public boolean anythingRestored() {
            return !restoredProtections.isEmpty() || !restoredWarNames.isEmpty();
        }
    }

    private BillingSettlementService() {
    }

    public static SettlementResult settle(MinecraftServer server, Team team) {
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID teamId = team.getTeamId();
        TeamQueuedChanges pendingState = savedData.getPendingState(teamId);
        IBankAccount account = BankLedgerAccess.getAccountForTeam(server, team);

        List<TeamProperty<?>> suspended = new ArrayList<>();
        boolean[] warsSuspended = {false};
        List<TeamProperty<?>> restored = new ArrayList<>();
        List<String> restoredWarNames = new ArrayList<>();
        List<TeamProperty<?>> unaffordable = new ArrayList<>();

        SafeguardEnforcementService.setApplying(true);
        try {
            pendingState = LandChunkService.applyPendingChunkTypes(server, team, pendingState);
            pendingState = applyUserWarEnds(server, team, savedData, pendingState);
            pendingState = restorePendingProtections(server, team, pendingState, account, restored, unaffordable);
            pendingState = restorePendingWars(server, team, savedData, pendingState, account, restoredWarNames);
            pendingState = dismantleOutgoingWarsUntilAffordable(server, team, savedData, pendingState, account, warsSuspended);
            pendingState = dismantleProtectionsUntilAffordable(server, team, pendingState, account, suspended);

            MoneyValue cost = ConflictService.calculateTotalUpkeepCost(server, team, pendingState);
            if (cost.isEmpty()) {
                savedData.setPendingState(teamId, pendingState);
                savedData.setProtectionLocked(teamId, false);
                syncState(server, team);
                return new SettlementResult(true, MoneyValue.empty(), pendingState, forceLoadCount(team), List.copyOf(suspended), warsSuspended[0], List.copyOf(restored), List.copyOf(restoredWarNames), List.copyOf(unaffordable));
            }

            if (!account.getMoneyStorage().containsValue(cost)) {
                QueuedChangeService.removeAllForceLoads(server, team);
                pendingState = clearForceLoadPending(pendingState);
                cost = ConflictService.calculateTotalUpkeepCost(server, team, pendingState);
            }

            if (!cost.isEmpty() && !account.getMoneyStorage().containsValue(cost)) {
                savedData.setPendingState(teamId, pendingState);
                savedData.setProtectionLocked(teamId, true);
                SafeguardEnforcementService.notifyTeam(server, team, "message.lc_claim_economy.upkeep_unpaid_frozen");
                syncState(server, team);
                savedData.recordUpkeepMissed();
                return new SettlementResult(false, MoneyValue.empty(), pendingState, forceLoadCount(team), List.copyOf(suspended), warsSuspended[0], List.copyOf(restored), List.copyOf(restoredWarNames), List.copyOf(unaffordable));
            }

            if (!cost.isEmpty()) {
                account.withdrawMoney(cost);
                long costCopper = cost.getCoreValue();
                savedData.recordUpkeepCharged(costCopper);
                BankLedgerAccess.logTransaction(account, false, cost, Component.translatable("message.lc_claim_economy.ledger.upkeep_charge"));
            }
            savedData.setPendingState(teamId, pendingState);
            savedData.setProtectionLocked(teamId, false);
            syncState(server, team);
            return new SettlementResult(true, cost, pendingState, forceLoadCount(team), List.copyOf(suspended), warsSuspended[0], List.copyOf(restored), List.copyOf(restoredWarNames), List.copyOf(unaffordable));
        } finally {
            SafeguardEnforcementService.setApplying(false);
        }
    }

    private static TeamQueuedChanges applyUserWarEnds(
            MinecraftServer server,
            Team team,
            LcClaimEconomySavedData savedData,
            TeamQueuedChanges pendingState
    ) {
        UUID teamId = team.getTeamId();
        Set<UUID> partners = new HashSet<>();
        TeamQueuedChanges updated = pendingState;
        for (UUID targetId : new HashSet<>(pendingState.pendingWarEnds())) {
            if (savedData.setWarTarget(teamId, targetId, false)) {
                partners.add(targetId);
                for (ServerPlayer member : team.getOnlineMembers()) {
                    dev.voidpulsar.lc_claim_economy.integration.quest.QuestAdvancements.grant(
                            member, dev.voidpulsar.lc_claim_economy.integration.quest.QuestAdvancements.warEnded());
                }
            }
            updated = updated.withoutPendingWarEnd(targetId);
        }
        refreshWarPartners(server, team, teamId, partners);
        return updated;
    }

    private static TeamQueuedChanges restorePendingProtections(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            IBankAccount account,
            List<TeamProperty<?>> restoredOut,
            List<TeamProperty<?>> unaffordableOut
    ) {
        TeamQueuedChanges updated = pendingState;

        for (TeamProperty<?> property : SafeguardDismantleSequence.restoreOrder()) {
            if (!SafeguardRollbackService.hasPendingApply(team, property, updated)) {
                continue;
            }
            if (!ConflictService.canAffordUpkeepWithPendingProperty(server, team, updated, account, property)) {
                // Wanted but can't afford — collect for notification.
                unaffordableOut.add(property);
                continue;
            }
            updated = SafeguardRollbackService.restoreProtection(server, team, property, updated);
            restoredOut.add(property);
        }

        if (ConflictService.canAffordUpkeep(server, team, updated, account)) {
            QueuedChangeService.applyPendingForceLoadsOnly(server, team, updated);
            updated = clearForceLoadPending(updated);
        }

        return updated;
    }

    private static TeamQueuedChanges restorePendingWars(
            MinecraftServer server,
            Team team,
            LcClaimEconomySavedData savedData,
            TeamQueuedChanges pendingState,
            IBankAccount account,
            List<String> restoredWarNamesOut
    ) {
        UUID teamId = team.getTeamId();
        List<UUID> restoreOrder = ConflictService.pendingWarRestoreOrder(server, team, pendingState, savedData);
        TeamQueuedChanges updated = pendingState;
        Set<UUID> partners = new HashSet<>();

        for (UUID targetId : restoreOrder) {
            if (!ConflictService.canAffordUpkeepWithOutgoingWar(server, team, updated, account, savedData, targetId)) {
                break;
            }
            savedData.setWarTarget(teamId, targetId, true);
            updated = updated.withoutPendingWarDeclare(targetId);
            partners.add(targetId);
            Team target = TeamRegistry.resolve(server, targetId);
            restoredWarNamesOut.add(target != null ? ConflictService.displayName(target) : targetId.toString());
        }

        if (!partners.isEmpty()) {
            refreshWarPartners(server, team, teamId, partners);
        }
        return updated;
    }

    private static TeamQueuedChanges dismantleOutgoingWarsUntilAffordable(
            MinecraftServer server,
            Team team,
            LcClaimEconomySavedData savedData,
            TeamQueuedChanges pendingState,
            IBankAccount account,
            boolean[] warsSuspendedOut
    ) {
        UUID teamId = team.getTeamId();
        TeamQueuedChanges updated = pendingState;
        List<UUID> dismantleOrder = ConflictService.outgoingWarDismantleOrder(server, team, savedData);
        Set<UUID> partners = new HashSet<>();
        boolean dismantledAny = false;

        for (UUID targetId : dismantleOrder) {
            if (ConflictService.canAffordUpkeep(server, team, updated, account)) {
                break;
            }
            if (updated.isPendingWarEnd(targetId) || !savedData.isAtWarWith(teamId, targetId)) {
                continue;
            }
            savedData.setWarTarget(teamId, targetId, false);
            updated = updated.withPendingWarDeclare(targetId);
            partners.add(targetId);
            dismantledAny = true;
        }

        if (dismantledAny) {
            refreshWarPartners(server, team, teamId, partners);
            warsSuspendedOut[0] = true;
        }
        return updated;
    }

    private static TeamQueuedChanges dismantleProtectionsUntilAffordable(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            IBankAccount account,
            List<TeamProperty<?>> suspendedOut
    ) {
        TeamQueuedChanges updated = pendingState;

        for (TeamProperty<?> property : SafeguardDismantleSequence.fullDismantleOrder()) {
            if (!SafeguardRollbackService.isLiveProtectionBillable(team, property)) {
                continue;
            }
            long reducedCost = ConflictService.calculateProtectionAndIncomingUpkeepCopper(server, team, updated);
            if (reducedCost <= 0L || account.getMoneyStorage().containsValue(CurrencyAmounts.fromCopper(reducedCost))) {
                break;
            }
            updated = SafeguardRollbackService.suspendProtection(server, team, property, updated);
            suspendedOut.add(property);
        }

        return updated;
    }

    private static Component protectionLabel(TeamProperty<?> property) {
        return Component.translatable(
                "message.lc_claim_economy.upkeep_priority.protection." + SafeguardPricing.propertyKey(property)
        );
    }

    private static TeamQueuedChanges clearForceLoadPending(TeamQueuedChanges pendingState) {
        TeamQueuedChanges updated = pendingState;
        for (String key : pendingState.pendingForceLoads()) {
            updated = updated.withoutPendingForceLoad(key);
        }
        for (String key : pendingState.pendingForceUnloads()) {
            updated = updated.withoutPendingForceUnload(key);
        }
        return updated;
    }

    private static int forceLoadCount(Team team) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        return chunkData.getForceLoadedChunks().size();
    }

    private static void refreshWarPartners(MinecraftServer server, Team team, UUID teamId, Set<UUID> partners) {
        if (partners.isEmpty()) {
            return;
        }
        ConflictSyncCoordinator.syncToTeam(server, teamId);
        ConflictSyncCoordinator.onUpkeepFactorsChanged(server, team);
        for (UUID partnerId : partners) {
            ConflictSyncCoordinator.syncToTeam(server, partnerId);
            Team partner = TeamRegistry.resolve(server, partnerId);
            if (partner != null) {
                ConflictSyncCoordinator.onUpkeepFactorsChanged(server, partner);
            }
        }
    }

    private static void syncState(MinecraftServer server, Team team) {
        QueuedStateBroadcast.syncTeam(server, team);
        ConflictSyncCoordinator.onUpkeepFactorsChanged(server, team);
    }
}
