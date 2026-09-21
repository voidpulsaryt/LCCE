package dev.voidpulsar.lc_claim_economy.handler;

import dev.architectury.event.CompoundEventResult;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.data.ChunkCoordKey;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateBroadcast;
import dev.voidpulsar.lc_claim_economy.service.SafeguardEnforcementService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;

public class ChunkLoadPinHandler {
    public ChunkLoadPinHandler() {
        ClaimedChunkEvent.BEFORE_LOAD.register(this::beforeLoad);
        ClaimedChunkEvent.BEFORE_UNLOAD.register(this::beforeUnload);
    }

    /** Everything both handlers need once the requester has been cleared to act on this chunk's team. */
    private record LoadScope(MinecraftServer server, LcClaimEconomySavedData economyData, TeamQueuedChanges queuedState, String positionKey) {
    }

    private CompoundEventResult<ClaimResult> beforeLoad(CommandSourceStack source, ClaimedChunk chunk) {
        CompoundEventResult<ClaimResult> denied = checkBasicAuthorization(source, chunk);
        if (denied != null) {
            return denied;
        }

        Team team = chunk.getTeamData().getTeam();
        LoadScope scope = resolveScope(team, chunk);
        if (scope == null) {
            return CompoundEventResult.pass();
        }

        if (scope.economyData().isProtectionLocked(team.getTeamId())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_claim_economy.protection_locked_change"));
        }

        // A second load click while one's already queued reads as "never mind" -
        // same idea as cycling a protection setting back to cancel it.
        CompoundEventResult<ClaimResult> toggledOff = tryCancelExisting(scope, team);
        if (toggledOff != null) {
            return toggledOff;
        }
        if (chunk.isForceLoaded()) {
            return CompoundEventResult.pass();
        }

        TeamQueuedChanges withNewLoad = scope.queuedState().withPendingForceLoad(scope.positionKey());
        if (!SafeguardEnforcementService.canAffordNextPeriod(scope.server(), team, withNewLoad)) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_claim_economy.insufficient_funds_protection"));
        }

        scope.economyData().setPendingState(team.getTeamId(), withNewLoad);
        LcClaimEconomy.LOGGER.debug("Team {}: force-load queued for chunk {}", team.getShortName(), scope.positionKey());
        QueuedStateBroadcast.syncTeam(scope.server(), team);
        notifyForceLoadPending(team);
        return CompoundEventResult.interruptFalse(ClaimResult.success());
    }

    private CompoundEventResult<ClaimResult> beforeUnload(CommandSourceStack source, ClaimedChunk chunk) {
        CompoundEventResult<ClaimResult> denied = checkBasicAuthorization(source, chunk);
        if (denied != null) {
            return denied;
        }

        Team team = chunk.getTeamData().getTeam();
        LoadScope scope = resolveScope(team, chunk);
        if (scope == null) {
            return CompoundEventResult.pass();
        }

        CompoundEventResult<ClaimResult> toggledOff = tryCancelExisting(scope, team);
        if (toggledOff != null) {
            return toggledOff;
        }
        if (!chunk.isForceLoaded()) {
            return CompoundEventResult.pass();
        }

        TeamQueuedChanges withNewUnload = scope.queuedState().withPendingForceUnload(scope.positionKey());
        scope.economyData().setPendingState(team.getTeamId(), withNewUnload);
        QueuedStateBroadcast.syncTeam(scope.server(), team);
        notifyForceLoadPending(team);
        return CompoundEventResult.interruptFalse(ClaimResult.success());
    }

    /** Requester must exist, FTB Teams must be up, the chunk must belong to a team, and that requester must hold rank on it. */
    @Nullable
    private CompoundEventResult<ClaimResult> checkBasicAuthorization(CommandSourceStack source, ClaimedChunk chunk) {
        ServerPlayer requester = source.getPlayer();
        if (requester == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return CompoundEventResult.pass();
        }
        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return CompoundEventResult.pass();
        }
        if (!BankLedgerAccess.canPurchaseForTeam(team, requester.getUUID())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_claim_economy.claim_rank_denied"));
        }
        return null;
    }

    @Nullable
    private LoadScope resolveScope(Team team, ClaimedChunk chunk) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        LcClaimEconomySavedData economyData = LcClaimEconomySavedData.get(server);
        TeamQueuedChanges queuedState = economyData.getPendingState(team.getTeamId());
        String positionKey = ChunkCoordKey.encode(chunk.getPos());
        return new LoadScope(server, economyData, queuedState, positionKey);
    }

    /** If a load or unload is already queued for this chunk, undo it and report the toggle-off; otherwise null. */
    @Nullable
    private CompoundEventResult<ClaimResult> tryCancelExisting(LoadScope scope, Team team) {
        if (scope.queuedState().isPendingForceLoad(scope.positionKey())) {
            return cancelQueuedChange(scope.server(), team, scope.economyData(), scope.queuedState().withoutPendingForceLoad(scope.positionKey()));
        }
        if (scope.queuedState().isPendingForceUnload(scope.positionKey())) {
            return cancelQueuedChange(scope.server(), team, scope.economyData(), scope.queuedState().withoutPendingForceUnload(scope.positionKey()));
        }
        return null;
    }

    /**
     * Shared tail end of the "undo a queued load/unload" branches above: persist
     * the state with that one entry removed, push it to the team's clients, tell
     * them it's cancelled, and let the underlying FTB Chunks action proceed as a
     * no-cost success (there's nothing left queued to charge for).
     */
    private CompoundEventResult<ClaimResult> cancelQueuedChange(
            MinecraftServer server, Team team, LcClaimEconomySavedData economyData, TeamQueuedChanges withoutEntry) {
        economyData.setPendingState(team.getTeamId(), withoutEntry);
        QueuedStateBroadcast.syncTeam(server, team);
        notifyForceLoadPendingCancelled(team);
        return CompoundEventResult.interruptFalse(ClaimResult.success());
    }

    private static void notifyForceLoadPending(Team team) {
        notifyTeam(team, "message.lc_claim_economy.forceload_change_pending");
    }

    private static void notifyForceLoadPendingCancelled(Team team) {
        notifyTeam(team, "message.lc_claim_economy.forceload_pending_cancelled");
    }

    private static void notifyTeam(Team team, String messageKey) {
        Component message = Component.translatable(messageKey);
        for (ServerPlayer member : team.getOnlineMembers()) {
            member.displayClientMessage(message, false);
        }
    }
}
