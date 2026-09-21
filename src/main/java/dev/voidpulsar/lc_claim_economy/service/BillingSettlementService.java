package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import net.minecraft.network.chat.Component;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.integration.quest.QuestAdvancements;
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
 * Runs one team's upkeep cycle end to end: bring back whatever they can now afford (queued
 * protections, then queued wars), and only once that's settled, start shedding whatever they
 * still can't afford (outgoing wars first, then protections one at a time by
 * {@link SafeguardDismantleSequence}) until the remaining bill fits their balance - dropping
 * queued force-loads as a last resort before finally freezing the team if even that isn't enough.
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

    /** Mutable scratch space threaded through one settlement run - swapped for a plain record since every step here needs to both read and extend the prior step's tallies. */
    private static final class Ledger {
        TeamQueuedChanges queuedState;
        final List<TeamProperty<?>> shedProtections = new ArrayList<>();
        final List<TeamProperty<?>> reinstatedProtections = new ArrayList<>();
        final List<String> reinstatedWarNames = new ArrayList<>();
        final List<TeamProperty<?>> tooExpensiveToReinstate = new ArrayList<>();
        boolean shedAnyWars;

        Ledger(TeamQueuedChanges initial) {
            queuedState = initial;
        }
    }

    private BillingSettlementService() {
    }

    public static SettlementResult settle(MinecraftServer server, Team team) {
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID teamId = team.getTeamId();
        IBankAccount account = BankLedgerAccess.getAccountForTeam(server, team);
        Ledger ledger = new Ledger(savedData.getPendingState(teamId));

        SafeguardEnforcementService.setApplying(true);
        try {
            ledger.queuedState = LandChunkService.applyPendingChunkTypes(server, team, ledger.queuedState);
            resolveUserRequestedWarEnds(server, team, savedData, ledger);
            reinstateAffordableProtections(server, team, account, ledger);
            reinstateAffordableWars(server, team, savedData, account, ledger);
            shedOutgoingWarsUntilAffordable(server, team, savedData, account, ledger);
            shedProtectionsUntilAffordable(server, team, account, ledger);

            return chargeOrFreeze(server, team, savedData, account, ledger);
        } finally {
            SafeguardEnforcementService.setApplying(false);
        }
    }

    /** The actual money-moving step, once every reinstate/shed decision above has already run. */
    private static SettlementResult chargeOrFreeze(
            MinecraftServer server,
            Team team,
            LcClaimEconomySavedData savedData,
            IBankAccount account,
            Ledger ledger
    ) {
        UUID teamId = team.getTeamId();
        MoneyValue owed = ConflictService.calculateTotalUpkeepCost(server, team, ledger.queuedState);

        if (owed.isEmpty()) {
            savedData.setPendingState(teamId, ledger.queuedState);
            savedData.setProtectionLocked(teamId, false);
            broadcastTeamState(server, team);
            return finish(team, ledger, true, MoneyValue.empty());
        }

        if (!account.getMoneyStorage().containsValue(owed)) {
            // Force-loading a chunk has no protection benefit of its own - it's the cheapest
            // thing to give up before resorting to a full freeze.
            QueuedChangeService.removeAllForceLoads(server, team);
            ledger.queuedState = dropQueuedForceLoadChanges(ledger.queuedState);
            owed = ConflictService.calculateTotalUpkeepCost(server, team, ledger.queuedState);
        }

        if (!owed.isEmpty() && !account.getMoneyStorage().containsValue(owed)) {
            savedData.setPendingState(teamId, ledger.queuedState);
            savedData.setProtectionLocked(teamId, true);
            SafeguardEnforcementService.notifyTeam(server, team, "message.lc_claim_economy.upkeep_unpaid_frozen");
            broadcastTeamState(server, team);
            savedData.recordUpkeepMissed();
            return finish(team, ledger, false, MoneyValue.empty());
        }

        if (!owed.isEmpty()) {
            account.withdrawMoney(owed);
            savedData.recordUpkeepCharged(owed.getCoreValue());
            BankLedgerAccess.logTransaction(account, false, owed, Component.translatable("message.lc_claim_economy.ledger.upkeep_charge"));
        }
        savedData.setPendingState(teamId, ledger.queuedState);
        savedData.setProtectionLocked(teamId, false);
        broadcastTeamState(server, team);
        return finish(team, ledger, true, owed);
    }

    private static SettlementResult finish(Team team, Ledger ledger, boolean paid, MoneyValue charged) {
        return new SettlementResult(
                paid,
                charged,
                ledger.queuedState,
                currentForceLoadCount(team),
                List.copyOf(ledger.shedProtections),
                ledger.shedAnyWars,
                List.copyOf(ledger.reinstatedProtections),
                List.copyOf(ledger.reinstatedWarNames),
                List.copyOf(ledger.tooExpensiveToReinstate)
        );
    }

    private static void resolveUserRequestedWarEnds(
            MinecraftServer server,
            Team team,
            LcClaimEconomySavedData savedData,
            Ledger ledger
    ) {
        UUID teamId = team.getTeamId();
        Set<UUID> endedWith = new HashSet<>();
        for (UUID targetId : new HashSet<>(ledger.queuedState.pendingWarEnds())) {
            if (savedData.setWarTarget(teamId, targetId, false)) {
                endedWith.add(targetId);
                for (ServerPlayer member : team.getOnlineMembers()) {
                    QuestAdvancements.grant(member, QuestAdvancements.warEnded());
                }
            }
            ledger.queuedState = ledger.queuedState.withoutPendingWarEnd(targetId);
        }
        notifyWarPartnersChanged(server, team, teamId, endedWith);
    }

    private static void reinstateAffordableProtections(
            MinecraftServer server,
            Team team,
            IBankAccount account,
            Ledger ledger
    ) {
        for (TeamProperty<?> property : SafeguardDismantleSequence.restoreOrder()) {
            if (!SafeguardRollbackService.hasPendingApply(team, property, ledger.queuedState)) {
                continue;
            }
            if (!ConflictService.canAffordUpkeepWithPendingProperty(server, team, ledger.queuedState, account, property)) {
                ledger.tooExpensiveToReinstate.add(property);
                continue;
            }
            ledger.queuedState = SafeguardRollbackService.restoreProtection(server, team, property, ledger.queuedState);
            ledger.reinstatedProtections.add(property);
        }

        if (ConflictService.canAffordUpkeep(server, team, ledger.queuedState, account)) {
            QueuedChangeService.applyPendingForceLoadsOnly(server, team, ledger.queuedState);
            ledger.queuedState = dropQueuedForceLoadChanges(ledger.queuedState);
        }
    }

    private static void reinstateAffordableWars(
            MinecraftServer server,
            Team team,
            LcClaimEconomySavedData savedData,
            IBankAccount account,
            Ledger ledger
    ) {
        UUID teamId = team.getTeamId();
        List<UUID> priorityOrder = ConflictService.pendingWarRestoreOrder(server, team, ledger.queuedState, savedData);
        Set<UUID> reinstatedWith = new HashSet<>();

        for (UUID targetId : priorityOrder) {
            if (!ConflictService.canAffordUpkeepWithOutgoingWar(server, team, ledger.queuedState, account, savedData, targetId)) {
                break;
            }
            savedData.setWarTarget(teamId, targetId, true);
            ledger.queuedState = ledger.queuedState.withoutPendingWarDeclare(targetId);
            reinstatedWith.add(targetId);
            Team target = TeamRegistry.resolve(server, targetId);
            ledger.reinstatedWarNames.add(target != null ? ConflictService.displayName(target) : targetId.toString());
        }

        if (!reinstatedWith.isEmpty()) {
            notifyWarPartnersChanged(server, team, teamId, reinstatedWith);
        }
    }

    private static void shedOutgoingWarsUntilAffordable(
            MinecraftServer server,
            Team team,
            LcClaimEconomySavedData savedData,
            IBankAccount account,
            Ledger ledger
    ) {
        UUID teamId = team.getTeamId();
        List<UUID> sheddingOrder = ConflictService.outgoingWarDismantleOrder(server, team, savedData);
        Set<UUID> droppedWith = new HashSet<>();

        for (UUID targetId : sheddingOrder) {
            if (ConflictService.canAffordUpkeep(server, team, ledger.queuedState, account)) {
                break;
            }
            if (ledger.queuedState.isPendingWarEnd(targetId) || !savedData.isAtWarWith(teamId, targetId)) {
                continue;
            }
            savedData.setWarTarget(teamId, targetId, false);
            ledger.queuedState = ledger.queuedState.withPendingWarDeclare(targetId);
            droppedWith.add(targetId);
        }

        if (!droppedWith.isEmpty()) {
            notifyWarPartnersChanged(server, team, teamId, droppedWith);
            ledger.shedAnyWars = true;
        }
    }

    private static void shedProtectionsUntilAffordable(
            MinecraftServer server,
            Team team,
            IBankAccount account,
            Ledger ledger
    ) {
        for (TeamProperty<?> property : SafeguardDismantleSequence.fullDismantleOrder()) {
            if (!SafeguardRollbackService.isLiveProtectionBillable(team, property)) {
                continue;
            }
            long remainingCopper = ConflictService.calculateProtectionAndIncomingUpkeepCopper(server, team, ledger.queuedState);
            boolean nowAffordable = remainingCopper <= 0L
                    || account.getMoneyStorage().containsValue(CurrencyAmounts.fromCopper(remainingCopper));
            if (nowAffordable) {
                break;
            }
            ledger.queuedState = SafeguardRollbackService.suspendProtection(server, team, property, ledger.queuedState);
            ledger.shedProtections.add(property);
        }
    }

    private static TeamQueuedChanges dropQueuedForceLoadChanges(TeamQueuedChanges queuedState) {
        TeamQueuedChanges result = queuedState;
        for (String key : queuedState.pendingForceLoads()) {
            result = result.withoutPendingForceLoad(key);
        }
        for (String key : queuedState.pendingForceUnloads()) {
            result = result.withoutPendingForceUnload(key);
        }
        return result;
    }

    private static int currentForceLoadCount(Team team) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        return chunkData.getForceLoadedChunks().size();
    }

    private static void notifyWarPartnersChanged(MinecraftServer server, Team team, UUID teamId, Set<UUID> partnerIds) {
        if (partnerIds.isEmpty()) {
            return;
        }
        ConflictSyncCoordinator.syncToTeam(server, teamId);
        ConflictSyncCoordinator.onUpkeepFactorsChanged(server, team);
        for (UUID partnerId : partnerIds) {
            ConflictSyncCoordinator.syncToTeam(server, partnerId);
            Team partner = TeamRegistry.resolve(server, partnerId);
            if (partner != null) {
                ConflictSyncCoordinator.onUpkeepFactorsChanged(server, partner);
            }
        }
    }

    private static void broadcastTeamState(MinecraftServer server, Team team) {
        QueuedStateBroadcast.syncTeam(server, team);
        ConflictSyncCoordinator.onUpkeepFactorsChanged(server, team);
    }
}
