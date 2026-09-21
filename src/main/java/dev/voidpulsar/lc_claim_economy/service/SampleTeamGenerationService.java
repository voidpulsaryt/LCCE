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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        List<Team> testTeams = teamManager.getTeams().stream()
                .filter(team -> matchesTestTeamPrefix(team.getName().getString()))
                .toList();

        long claimedCount = FTBChunksAPI.api().isManagerLoaded()
                ? testTeams.stream().filter(team -> ConflictService.isClaimTeam(server, team)).count()
                : 0L;

        long filledSlots = IntStream.rangeClosed(1, DEFAULT_COUNT)
                .filter(slot -> resolveSlot(teamManager, slot) != null)
                .count();

        return new CountResult(testTeams.size(), (int) claimedCount, (int) filledSlots);
    }

    public static List<Team> findAllTestTeams(TeamManager teamManager) {
        return teamManager.getTeams().stream()
                .filter(team -> matchesTestTeamPrefix(team.getName().getString()))
                .sorted(Comparator.comparing(team -> team.getName().getString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
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
            switch (deleteOneTestTeam(team, deleteSource)) {
                case DELETED -> deletedCount++;
                case SKIPPED -> skippedCount++;
                case FAILED -> failedCount++;
            }
        }

        if (runner != null) {
            ConflictSyncCoordinator.syncToPlayer(runner);
        }

        return new ClearResult(deletedCount, skippedCount, failedCount);
    }

    private enum ClearOutcome {
        DELETED, SKIPPED, FAILED
    }

    private static ClearOutcome deleteOneTestTeam(Team team, CommandSourceStack deleteSource) {
        if (!(team instanceof ServerTeam serverTeam)) {
            LcClaimEconomy.LOGGER.warn("Refusing to delete non-server test team {}", team.getName().getString());
            return ClearOutcome.SKIPPED;
        }
        try {
            serverTeam.delete(deleteSource);
            return ClearOutcome.DELETED;
        } catch (Exception exception) {
            LcClaimEconomy.LOGGER.warn("Failed to delete test team {}", team.getName().getString(), exception);
            return ClearOutcome.FAILED;
        }
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
            switch (seedOneSlot(slot, teamManager, source, claimSource, dimension)) {
                case CREATED -> createdCount++;
                case SKIPPED -> skippedCount++;
                case FAILED -> failedCount++;
            }
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

    private enum SeedOutcome {
        CREATED, SKIPPED, FAILED
    }

    private static SeedOutcome seedOneSlot(
            int slot,
            TeamManager teamManager,
            CommandSourceStack source,
            CommandSourceStack claimSource,
            ResourceKey<Level> dimension
    ) throws CommandSyntaxException {
        String slotName = slotName(slot);
        Team team = resolveSlot(teamManager, slot);
        if (team != null) {
            ChunkTeamData existingClaims = FTBChunksAPI.api().getManager().getOrCreateData(team);
            if (!existingClaims.getClaimedChunks().isEmpty()) {
                return SeedOutcome.SKIPPED;
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
            LcClaimEconomy.LOGGER.warn("Failed to claim chunk for {} at {}: {}", slotName, claimPos, claimResult.getResultId());
            return SeedOutcome.FAILED;
        }
        return SeedOutcome.CREATED;
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

        List<Team> candidates = IntStream.rangeClosed(1, slotLimit)
                .mapToObj(slot -> resolveSlot(teamManager, slot))
                .filter(Objects::nonNull)
                .filter(slotTeam -> ConflictService.isClaimTeam(server, slotTeam))
                .collect(Collectors.toCollection(ArrayList::new));
        if (candidates.isEmpty()) {
            candidates = findAllTestTeams(teamManager).stream()
                    .filter(team -> ConflictService.isClaimTeam(server, team))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        clearDemoWarLinks(savedData, runnerTeamId, candidates);

        int incomingCount = Math.min(DEMO_INCOMING_COUNT, candidates.size());
        int outgoingCount = Math.min(DEMO_OUTGOING_COUNT, Math.max(0, candidates.size() - incomingCount));

        List<Team> finalCandidates = candidates;
        IntStream.range(0, incomingCount)
                .forEach(i -> savedData.setWarTarget(finalCandidates.get(i).getTeamId(), runnerTeamId, true));

        IntStream.range(0, outgoingCount)
                .forEach(i -> savedData.setWarTarget(runnerTeamId, finalCandidates.get(incomingCount + i).getTeamId(), true));

        int availableTargets = Math.max(0, candidates.size() - incomingCount - outgoingCount);
        return new DemoWarPlan(incomingCount, outgoingCount, availableTargets);
    }

    /** Clears any war link between {@code centerTeamId} and the given demo teams, in either direction. */
    private static void clearDemoWarLinks(LcClaimEconomySavedData savedData, UUID centerTeamId, List<Team> demoTeams) {
        Set<UUID> demoTeamIds = demoTeams.stream()
                .map(Team::getTeamId)
                .collect(Collectors.toCollection(HashSet::new));

        new HashSet<>(savedData.getWarTargets(centerTeamId)).stream()
                .filter(demoTeamIds::contains)
                .forEach(targetId -> savedData.setWarTarget(centerTeamId, targetId, false));

        demoTeams.stream()
                .map(Team::getTeamId)
                .filter(demoTeamId -> savedData.isAtWarWith(demoTeamId, centerTeamId))
                .forEach(demoTeamId -> savedData.setWarTarget(demoTeamId, centerTeamId, false));
    }
}
