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
        reconcileTeams(event.getServer());
    }

    private void onTeamCreated(TeamCreatedEvent event) {
        ClaimVisibilityRules.ensurePublic(event.getTeam());
        ensureAccount(event.getTeam());
    }

    private void onTeamLoaded(TeamEvent event) {
        ClaimVisibilityRules.ensurePublic(event.getTeam());
        ensureAccount(event.getTeam());
    }

    private void onTeamDeleted(TeamEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            TeamPurgeService.purge(server, event.getTeam());
        }
    }

    private void onPlayerJoinedParty(PlayerJoinedPartyTeamEvent event) {
        ensureAccount(event.getTeam());
    }

    private void onPlayerLeftParty(PlayerLeftPartyTeamEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && event.getTeamDeleted()) {
            PartyDissolutionSettlement.settle(server, event.getTeam());
            if (event.getPlayer() != null) {
                ConflictSyncCoordinator.syncToPlayer(event.getPlayer());
            }
        }

        if (!event.getTeamDeleted()) {
            ensureAccount(event.getTeam());
        }
        ensureAccount(event.getPlayerTeam());
    }

    private void onOwnershipTransferred(PlayerTransferredTeamOwnershipEvent event) {
        ensureAccount(event.getTeam());
    }

    private void onPlayerChanged(PlayerChangedTeamEvent event) {
        ensureAccount(event.getTeam());
        event.getPreviousTeam().ifPresent(this::ensureAccount);
    }

    private void onPlayerLoggedInAfterTeam(PlayerLoggedInAfterTeamEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            TeamBankLinkRegistry.reconcile(server);
        }
        ensureAccount(event.getTeam());
        if (event.getPlayer() != null) {
            ServerPlayer player = event.getPlayer();
            ClaimPricingBroadcast.syncToPlayer(player);
            QueuedStateBroadcast.syncToPlayer(player);
            dev.voidpulsar.lc_claim_economy.service.LandChunkService.syncToPlayer(player);
            ConflictSyncCoordinator.syncToPlayer(player);
            // Defer once: login sync can race the client's play handler registration
            // after a full server restart + reconnect.
            server.execute(() -> dev.voidpulsar.lc_claim_economy.service.LandChunkService.syncToPlayer(player));
        }
    }

    private void reconcileTeams(MinecraftServer server) {
        TeamBankLinkRegistry.reconcile(server);
    }

    private void ensureAccount(Team team) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || team == null || !team.isValid()) {
            return;
        }
        BankLedgerAccess.ensurePartyAccountExists(server, team);
    }
}
