package dev.voidpulsar.lc_claim_economy.service;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.data.ServerTeam;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Backs the debug-only {@code /lcce seed_test_teams}/{@code clear_test_teams}/
 * {@code count_test_teams} commands (see {@code debugTestTeamCommands} config, off by
 * default and meant to stay off on production servers). Spins up numbered "WarTestNN"
 * teams with one claimed chunk each and a preset mix of incoming/outgoing wars against
 * whichever team ran the command, purely so the war UI has realistic-looking data to
 * develop and screenshot against without needing a real multi-team server session.
 */
public final class SampleTeamGenerationService {
    public static final String TEAM_PREFIX = "WarTest";
    public static final int DEFAULT_COUNT = 20;
    private static final int DEMO_INCOMING_COUNT = 4;
    private static final int DEMO_OUTGOING_COUNT = 3;
    private static final int DEMO_CHUNK_ORIGIN_X = 2000;
    private static final int DEMO_CHUNK_ORIGIN_Z = 2000;

    // Nord palette, cycled by slot index - just needs to look distinct team-to-team, no
    // significance beyond that.
    private static final Color4I[] SLOT_COLORS = {
            Color4I.rgb(0xBF616A),
            Color4I.rgb(0xD08770),
            Color4I.rgb(0xEBCB8B),
            Color4I.rgb(0xA3BE8C),
            Color4I.rgb(0x88C0D0),
            Color4I.rgb(0xB48EAD),
            Color4I.rgb(0x5E81AC),
            Color4I.rgb(0x8FBCBB)
    };

    public record SeedResult(
            int created,
            int skipped,
            int failed,
            int incomingWars,
            int outgoingWars,
            int availableTargets
    ) {
    }

    public record ClearResult(int deleted, int skipped, int failed) {
    }

    public record CountResult(int total, int withClaims, int inDefaultRange) {
    }

    private record DemoWarPlan(int incomingCount, int outgoingCount, int availableTargets) {
    }

    private SampleTeamGenerationService() {
    }

    public static CountResult count(MinecraftServer server) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return new CountResult(0, 0, 0);
        }

        TeamManager teamManager = FTBTeamsAPI.api().getManager();
        int totalCount = 0;
        int claimedCount = 0;
        for (Team team : teamManager.getTeams()) {
            if (!matchesTestTeamPrefix(team.getName().getString())) {
                continue;
            }
            totalCount++;
            if (FTBChunksAPI.api().isManagerLoaded() && ConflictService.isClaimTeam(server, team)) {
                claimedCount++;
            }
        }

        int filledSlots = 0;
        for (int slot = 1; slot <= DEFAULT_COUNT; slot++) {
            if (resolveSlot(teamManager, slot) != null) {
                filledSlots++;
            }
        }

        return new CountResult(totalCount, claimedCount, filledSlots);
    }

    public static List<Team> findAllTestTeams(TeamManager teamManager) {
        List<Team> matches = new ArrayList<>();
        for (Team team : teamManager.getTeams()) {
            if (matchesTestTeamPrefix(team.getName().getString())) {
                matches.add(team);
            }
        }
        matches.sort(Comparator.comparing(team -> team.getName().getString(), String.CASE_INSENSITIVE_ORDER));
        return matches;
    }

    @Nullable
    private static Team resolveSlot(TeamManager teamManager, int slot) {
        String slotName = slotName(slot);
        Team byName = teamManager.getTeamByName(slotName).orElse(null);
        if (byName != null) {
            return byName;
        }
        for (Team team : teamManager.getTeams()) {
            if (slotName.equalsIgnoreCase(team.getName().getString())) {
                return team;
            }
        }
        return null;
    }

    private static boolean matchesTestTeamPrefix(String name) {
        return name.startsWith(TEAM_PREFIX);
    }

    private static String slotName(int slot) {
        return TEAM_PREFIX + String.format("%02d", slot);
    }

    public static ClearResult clear(MinecraftServer server, CommandSourceStack source, int limit) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return new ClearResult(0, 0, 0);
        }

        TeamManager teamManager = FTBTeamsAPI.api().getManager();
        CommandSourceStack deleteSource = server.createCommandSourceStack().withPermission(4);
        List<Team> toDelete = findAllTestTeams(teamManager);
        if (limit > 0 && toDelete.size() > limit) {
            toDelete = new ArrayList<>(toDelete.subList(0, limit));
        }

        ServerPlayer runner = source.getEntity() instanceof ServerPlayer player ? player : null;
        if (runner != null) {
            Team runnerTeam = teamManager.getTeamForPlayer(runner).orElse(null);
            if (runnerTeam != null) {
                clearDemoWarLinks(LcClaimEconomySavedData.get(server), runnerTeam.getTeamId(), toDelete);
            }
        }

        int deletedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (Team team : toDelete) {
            if (!(team instanceof ServerTeam serverTeam)) {
                skippedCount++;
                LcClaimEconomy.LOGGER.warn("Refusing to delete non-server test team {}", team.getName().getString());
                continue;
            }

            try {
                serverTeam.delete(deleteSource);
                deletedCount++;
            } catch (Exception exception) {
                failedCount++;
                LcClaimEconomy.LOGGER.warn("Failed to delete test team {}", team.getName().getString(), exception);
            }
        }

        if (runner != null) {
            ConflictSyncCoordinator.syncToPlayer(runner);
        }

        return new ClearResult(deletedCount, skippedCount, failedCount);
    }

    public static SeedResult seed(MinecraftServer server, CommandSourceStack source, int requestedCount) throws CommandSyntaxException {
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return new SeedResult(0, 0, 0, 0, 0, 0);
        }

        TeamManager teamManager = FTBTeamsAPI.api().getManager();
        ResourceKey<Level> dimension = source.getEntity() != null
                ? source.getEntity().level().dimension()
                : Level.OVERWORLD;
        CommandSourceStack claimSource = server.createCommandSourceStack().withPermission(4);

        int createdCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (int slot = 1; slot <= requestedCount; slot++) {
            String slotName = slotName(slot);
            Team team = resolveSlot(teamManager, slot);
            if (team != null) {
                ChunkTeamData existingClaims = FTBChunksAPI.api().getManager().getOrCreateData(team);
                if (!existingClaims.getClaimedChunks().isEmpty()) {
                    skippedCount++;
                    continue;
                }
            } else {
                team = teamManager.createServerTeam(
                        source,
                        slotName,
                        "Dev test team for war UI",
                        SLOT_COLORS[(slot - 1) % SLOT_COLORS.length]
                );
            }

            ChunkDimPos claimPos = new ChunkDimPos(dimension, DEMO_CHUNK_ORIGIN_X + slot, DEMO_CHUNK_ORIGIN_Z);
            ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
            ClaimResult claimResult = chunkData.claim(claimSource, claimPos, false);
            if (!claimResult.isSuccess()) {
                failedCount++;
                LcClaimEconomy.LOGGER.warn("Failed to claim chunk for {} at {}: {}", slotName, claimPos, claimResult.getResultId());
                continue;
            }

            createdCount++;
        }

        ServerPlayer runner = source.getEntity() instanceof ServerPlayer player ? player : null;
        DemoWarPlan warPlan = seedDemoWars(server, runner, requestedCount);
        if (runner != null) {
            ConflictSyncCoordinator.syncToPlayer(runner);
        }

        return new SeedResult(
                createdCount,
                skippedCount,
                failedCount,
                warPlan.incomingCount(),
                warPlan.outgoingCount(),
                warPlan.availableTargets()
        );
    }

    private static DemoWarPlan seedDemoWars(MinecraftServer server, @Nullable ServerPlayer runner, int slotLimit) {
        if (!ConflictService.isEnabled()) {
            return new DemoWarPlan(0, 0, 0);
        }
        if (runner == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return new DemoWarPlan(0, 0, 0);
        }

        Team runnerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(runner).orElse(null);
        if (runnerTeam == null || !ConflictService.isClaimTeam(server, runnerTeam)) {
            LcClaimEconomy.LOGGER.info("Skipped demo war seeding: {} has no claimed chunks", runner.getGameProfile().getName());
            return new DemoWarPlan(0, 0, 0);
        }

        TeamManager teamManager = FTBTeamsAPI.api().getManager();
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID runnerTeamId = runnerTeam.getTeamId();

        List<Team> candidates = new ArrayList<>();
        for (int slot = 1; slot <= slotLimit; slot++) {
            Team slotTeam = resolveSlot(teamManager, slot);
            if (slotTeam != null && ConflictService.isClaimTeam(server, slotTeam)) {
                candidates.add(slotTeam);
            }
        }
        if (candidates.isEmpty()) {
            for (Team team : findAllTestTeams(teamManager)) {
                if (ConflictService.isClaimTeam(server, team)) {
                    candidates.add(team);
                }
            }
        }

        clearDemoWarLinks(savedData, runnerTeamId, candidates);

        int incomingCount = Math.min(DEMO_INCOMING_COUNT, candidates.size());
        int outgoingCount = Math.min(DEMO_OUTGOING_COUNT, Math.max(0, candidates.size() - incomingCount));

        for (int i = 0; i < incomingCount; i++) {
            savedData.setWarTarget(candidates.get(i).getTeamId(), runnerTeamId, true);
        }

        for (int i = 0; i < outgoingCount; i++) {
            savedData.setWarTarget(runnerTeamId, candidates.get(incomingCount + i).getTeamId(), true);
        }

        int availableTargets = Math.max(0, candidates.size() - incomingCount - outgoingCount);
        return new DemoWarPlan(incomingCount, outgoingCount, availableTargets);
    }

    /** Clears any war link between {@code centerTeamId} and the given demo teams, in either direction. */
    private static void clearDemoWarLinks(LcClaimEconomySavedData savedData, UUID centerTeamId, List<Team> demoTeams) {
        Set<UUID> demoTeamIds = new HashSet<>();
        for (Team team : demoTeams) {
            demoTeamIds.add(team.getTeamId());
        }

        for (UUID targetId : new HashSet<>(savedData.getWarTargets(centerTeamId))) {
            if (demoTeamIds.contains(targetId)) {
                savedData.setWarTarget(centerTeamId, targetId, false);
            }
        }

        for (Team team : demoTeams) {
            UUID demoTeamId = team.getTeamId();
            if (savedData.isAtWarWith(demoTeamId, centerTeamId)) {
                savedData.setWarTarget(demoTeamId, centerTeamId, false);
            }
        }
    }
}
