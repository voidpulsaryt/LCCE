package dev.voidpulsar.lc_claim_economy.handler;

import dev.architectury.event.CompoundEventResult;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.event.ClaimedChunkEvent;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
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

public class ChunkLoadPinHandler {
    public ChunkLoadPinHandler() {
        ClaimedChunkEvent.BEFORE_LOAD.register(this::beforeLoad);
        ClaimedChunkEvent.BEFORE_UNLOAD.register(this::beforeUnload);
    }

    private CompoundEventResult<ClaimResult> beforeLoad(CommandSourceStack source, ClaimedChunk chunk) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return CompoundEventResult.pass();
        }

        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return CompoundEventResult.pass();
        }

        if (!BankLedgerAccess.canPurchaseForTeam(team, player.getUUID())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_claim_economy.claim_rank_denied"));
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return CompoundEventResult.pass();
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        if (savedData.isProtectionLocked(team.getTeamId())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_claim_economy.protection_locked_change"));
        }

        TeamQueuedChanges pendingState = savedData.getPendingState(team.getTeamId());
        String chunkKey = ChunkCoordKey.encode(chunk.getPos());

        // Toggle off a queued load, or undo a queued unload (same idea as cycling
        // a protection setting back while a change is pending).
        if (pendingState.isPendingForceLoad(chunkKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingForceLoad(chunkKey));
            QueuedStateBroadcast.syncTeam(server, team);
            notifyForceLoadPendingCancelled(team);
            return CompoundEventResult.interruptFalse(ClaimResult.success());
        }
        if (pendingState.isPendingForceUnload(chunkKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingForceUnload(chunkKey));
            QueuedStateBroadcast.syncTeam(server, team);
            notifyForceLoadPendingCancelled(team);
            return CompoundEventResult.interruptFalse(ClaimResult.success());
        }

        if (chunk.isForceLoaded()) {
            return CompoundEventResult.pass();
        }

        TeamQueuedChanges updated = pendingState.withPendingForceLoad(chunkKey);
        if (!SafeguardEnforcementService.canAffordNextPeriod(server, team, updated)) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_claim_economy.insufficient_funds_protection"));
        }

        savedData.setPendingState(team.getTeamId(), updated);
        dev.voidpulsar.lc_claim_economy.LcClaimEconomy.LOGGER.debug("Team {}: force-load queued for chunk {}",
                team.getShortName(), chunkKey);
        QueuedStateBroadcast.syncTeam(server, team);
        notifyForceLoadPending(team);
        return CompoundEventResult.interruptFalse(ClaimResult.success());
    }

    private CompoundEventResult<ClaimResult> beforeUnload(CommandSourceStack source, ClaimedChunk chunk) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return CompoundEventResult.pass();
        }

        Team team = chunk.getTeamData().getTeam();
        if (team == null) {
            return CompoundEventResult.pass();
        }

        if (!BankLedgerAccess.canPurchaseForTeam(team, player.getUUID())) {
            return CompoundEventResult.interruptFalse(ClaimResult.customProblem("message.lc_claim_economy.claim_rank_denied"));
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return CompoundEventResult.pass();
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        TeamQueuedChanges pendingState = savedData.getPendingState(team.getTeamId());
        String chunkKey = ChunkCoordKey.encode(chunk.getPos());

        if (pendingState.isPendingForceUnload(chunkKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingForceUnload(chunkKey));
            QueuedStateBroadcast.syncTeam(server, team);
            notifyForceLoadPendingCancelled(team);
            return CompoundEventResult.interruptFalse(ClaimResult.success());
        }

        if (pendingState.isPendingForceLoad(chunkKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingForceLoad(chunkKey));
            QueuedStateBroadcast.syncTeam(server, team);
            notifyForceLoadPendingCancelled(team);
            return CompoundEventResult.interruptFalse(ClaimResult.success());
        }

        if (!chunk.isForceLoaded()) {
            return CompoundEventResult.pass();
        }

        TeamQueuedChanges updated = pendingState.withPendingForceUnload(chunkKey);
        savedData.setPendingState(team.getTeamId(), updated);
        QueuedStateBroadcast.syncTeam(server, team);
        notifyForceLoadPending(team);
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
