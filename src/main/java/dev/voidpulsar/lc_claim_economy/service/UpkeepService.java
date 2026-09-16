package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankAccountHelper;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.config.UpkeepOnlineRequirement;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamPendingState;
import dev.voidpulsar.lc_claim_economy.teams.FtbTeamCatalog;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

public class UpkeepService {

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }

        long periodTicks = LcClaimEconomyConfig.SERVER.upkeepPeriodMinutes.get() * 60L * 20L;
        long gameTime = server.overworld().getGameTime();
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UpkeepOnlineRequirement requirement = LcClaimEconomyConfig.SERVER.upkeepOnlineRequirement.get();

        for (Team team : FtbTeamCatalog.trackedTeams(server)) {
            try {
                tickTeam(server, team, savedData, requirement, gameTime, periodTicks);
            } catch (Exception e) {
                LcClaimEconomy.LOGGER.error("Failed to process upkeep for team {}", team.getId(), e);
            }
        }
    }

    /**
     * Each team gets its own countdown (rather than one shared server-wide clock) so that
     * {@link UpkeepOnlineRequirement#TEAM_MEMBER_ONLINE} can pause a single team's billing
     * independently of every other team's online members.
     */
    private void tickTeam(
            MinecraftServer server,
            Team team,
            LcClaimEconomySavedData savedData,
            UpkeepOnlineRequirement requirement,
            long gameTime,
            long periodTicks
    ) {
        UUID teamId = team.getTeamId();
        long nextUpkeepTick = savedData.getNextUpkeepTick(teamId);

        if (nextUpkeepTick < 0L) {
            savedData.setNextUpkeepTick(teamId, gameTime + periodTicks);
            return;
        }

        if (isPaused(server, team, requirement)) {
            savedData.setNextUpkeepTick(teamId, nextUpkeepTick + 1);
            return;
        }

        if (gameTime < nextUpkeepTick) {
            return;
        }

        savedData.setNextUpkeepTick(teamId, gameTime + periodTicks);
        processTeamUpkeep(server, team);
    }

    private static boolean isPaused(MinecraftServer server, Team team, UpkeepOnlineRequirement requirement) {
        return switch (requirement) {
            case ALWAYS_CHARGE -> false;
            case ANYONE_ONLINE -> server.getPlayerList().getPlayerCount() <= 0;
            case TEAM_MEMBER_ONLINE -> !hasOnlineMember(server, team);
        };
    }

    private static boolean hasOnlineMember(MinecraftServer server, Team team) {
        for (UUID memberId : team.getMembers()) {
            if (server.getPlayerList().getPlayer(memberId) != null) {
                return true;
            }
        }
        return false;
    }

    private void processTeamUpkeep(MinecraftServer server, Team team) {
        if (!team.isValid()) {
            return;
        }

        BankAccountHelper.ensurePartyAccountExists(server, team);
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());

        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        int chunkCount = chunkData.getClaimedChunks().size();
        int forceLoadCount = ProtectionPricing.countEffectiveForceLoads(chunkData, pendingState);

        if (chunkCount <= 0 && forceLoadCount <= 0 && pendingState.isEmpty()) {
            ProtectionService.tryUnlock(server, team);
            return;
        }

        MoneyValue projectedCost = WarService.calculateTotalUpkeepCost(server, team, pendingState);
        LcClaimEconomy.LOGGER.info("[PendingDebug] Upkeep for team {}: chunks={}, forceLoads={}, cost={}, pendingEmpty={}",
                team.getShortName(), chunkCount, forceLoadCount, projectedCost.getString(), pendingState.isEmpty());

        UpkeepSettlementService.SettlementResult result = UpkeepSettlementService.settle(server, team);

        if (result.anythingRestored()) {
            ProtectionService.notifyTeam(server, team,
                    UpkeepMessageBuilder.buildRestorationSummary(result.restoredProtections(), result.restoredWarNames()));
        }

        if (result.anythingSuspended()) {
            ProtectionService.notifyTeam(server, team,
                    UpkeepMessageBuilder.buildSuspensionSummary(result.suspendedProtections(), result.warsSuspended()));
        }

        if (!result.unaffordableRestorations().isEmpty()) {
            ProtectionService.notifyTeam(server, team,
                    UpkeepMessageBuilder.buildUnaffordableRestorationMessage(result.unaffordableRestorations()));
        }

        UpkeepBreakdown breakdown = UpkeepBreakdown.capture(
                server,
                team,
                result.forceLoadCount(),
                result.paid() ? result.charged() : MoneyValue.empty(),
                result.pendingState()
        );
        UpkeepBreakdownStore.store(breakdown);

        if (!result.paid()) {
            return;
        }

        if (result.charged().isEmpty()) {
            ProtectionService.tryUnlock(server, team);
            return;
        }

        ProtectionService.tryUnlock(server, team);
        ProtectionService.notifyTeamManagers(server, team, UpkeepMessageBuilder.buildSummary(breakdown));
    }
}
