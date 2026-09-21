package dev.voidpulsar.lc_claim_economy.handler;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.PlayerChangedTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerJoinedPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerLeftPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerLoggedInAfterTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerTransferredTeamOwnershipEvent;
import dev.ftb.mods.ftbteams.api.event.TeamCreatedEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.service.ClaimPricingBroadcast;
import dev.voidpulsar.lc_claim_economy.service.ClaimVisibilityRules;
import dev.voidpulsar.lc_claim_economy.service.PartyDissolutionSettlement;
import dev.voidpulsar.lc_claim_economy.service.TeamPurgeService;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateBroadcast;
import dev.voidpulsar.lc_claim_economy.service.ConflictSyncCoordinator;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.teams.CurrencyTeamLinkService;
import dev.voidpulsar.lc_claim_economy.teams.TeamBankLinkRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Every FTB Teams lifecycle event a team's bank account or claim-visibility
 * setting could depend on funnels through here, so the party bank account is
 * self-healing: whatever order teams load, players log in, or membership
 * changes happen in, {@link #guaranteeAccountExists} gets a chance to create
 * the missing account rather than leaving a team with claims but no ledger.
 */
public class TeamLifecycleWatcher {
    public TeamLifecycleWatcher() {
        TeamEvent.CREATED.register(this::onTeamCreated);
        TeamEvent.LOADED.register(this::onTeamLoaded);
        TeamEvent.DELETED.register(this::onTeamDeleted);
        TeamEvent.PLAYER_JOINED_PARTY.register(this::onPlayerJoinedParty);
        TeamEvent.PLAYER_LEFT_PARTY.register(this::onPlayerLeftParty);
        TeamEvent.OWNERSHIP_TRANSFERRED.register(this::onOwnershipTransferred);
        TeamEvent.PLAYER_CHANGED.register(this::onPlayerChanged);
        TeamEvent.PLAYER_LOGGED_IN.register(this::onPlayerLoggedInAfterTeam);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        TeamBankLinkRegistry.reconcile(event.getServer());
    }

    private void onTeamCreated(TeamCreatedEvent event) {
        initializeTeamDefaults(event.getTeam());
    }

    private void onTeamLoaded(TeamEvent event) {
        initializeTeamDefaults(event.getTeam());
    }

    private void initializeTeamDefaults(Team team) {
        ClaimVisibilityRules.ensurePublic(team);
        guaranteeAccountExists(team);
    }

    private void onTeamDeleted(TeamEvent event) {
        MinecraftServer activeServer = ServerLifecycleHooks.getCurrentServer();
        if (activeServer != null) {
            TeamPurgeService.purge(activeServer, event.getTeam());
        }
    }

    private void onPlayerJoinedParty(PlayerJoinedPartyTeamEvent event) {
        guaranteeAccountExists(event.getTeam());
    }

    private void onPlayerLeftParty(PlayerLeftPartyTeamEvent event) {
        MinecraftServer activeServer = ServerLifecycleHooks.getCurrentServer();
        if (event.getTeamDeleted()) {
            settlePartyDissolution(activeServer, event);
        } else {
            guaranteeAccountExists(event.getTeam());
        }
        // The player's new solo/fallback team also needs an account regardless
        // of whether the party they left still exists.
        guaranteeAccountExists(event.getPlayerTeam());
    }

    private void settlePartyDissolution(MinecraftServer activeServer, PlayerLeftPartyTeamEvent event) {
        if (activeServer == null) {
            return;
        }
        PartyDissolutionSettlement.settle(activeServer, event.getTeam());
        if (event.getPlayer() != null) {
            ConflictSyncCoordinator.syncToPlayer(event.getPlayer());
        }
    }

    private void onOwnershipTransferred(PlayerTransferredTeamOwnershipEvent event) {
        guaranteeAccountExists(event.getTeam());
    }

    private void onPlayerChanged(PlayerChangedTeamEvent event) {
        guaranteeAccountExists(event.getTeam());
        event.getPreviousTeam().ifPresent(this::guaranteeAccountExists);
    }

    private void onPlayerLoggedInAfterTeam(PlayerLoggedInAfterTeamEvent event) {
        MinecraftServer activeServer = ServerLifecycleHooks.getCurrentServer();
        if (activeServer != null) {
            TeamBankLinkRegistry.reconcile(activeServer);
        }
        guaranteeAccountExists(event.getTeam());

        ServerPlayer arrivingPlayer = event.getPlayer();
        if (arrivingPlayer == null) {
            return;
        }
        syncArrivalState(activeServer, arrivingPlayer);
    }

    private void syncArrivalState(MinecraftServer activeServer, ServerPlayer arrivingPlayer) {
        ClaimPricingBroadcast.syncToPlayer(arrivingPlayer);
        QueuedStateBroadcast.syncToPlayer(arrivingPlayer);
        dev.voidpulsar.lc_claim_economy.service.LandChunkService.syncToPlayer(arrivingPlayer);
        ConflictSyncCoordinator.syncToPlayer(arrivingPlayer);
        // Retry once on the next server tick: right after a full restart, the
        // client's play-phase packet handler isn't guaranteed to be registered
        // yet when this login event fires, so the first sync can be dropped.
        activeServer.execute(() -> dev.voidpulsar.lc_claim_economy.service.LandChunkService.syncToPlayer(arrivingPlayer));
    }

    private void guaranteeAccountExists(Team candidateTeam) {
        MinecraftServer activeServer = ServerLifecycleHooks.getCurrentServer();
        if (activeServer == null || candidateTeam == null || !candidateTeam.isValid()) {
            return;
        }
        BankLedgerAccess.ensurePartyAccountExists(activeServer, candidateTeam);
    }
}
