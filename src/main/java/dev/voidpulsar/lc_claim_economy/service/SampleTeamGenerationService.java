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

public final class SampleTeamGenerationService {
    public static final String TEAM_PREFIX = "WarTest";
    public static final int DEFAULT_COUNT = 20;
    private static final int DEMO_INCOMING_WARS = 4;
    private static final int DEMO_OUTGOING_WARS = 3;
    private static final int CHUNK_BASE_X = 2000;
    private static final int CHUNK_BASE_Z = 2000;

    private static final Color4I[] TEAM_COLORS = {
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

    private SampleTeamGenerationService() {
    }

    public static CountResult count(MinecraftServer server) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return new CountResult(0, 0, 0);
        }

        TeamManager manager = FTBTeamsAPI.api().getManager();
        int total = 0;
        int withClaims = 0;
        for (Team team : manager.getTeams()) {
            if (!isTestTeamName(team.getName().getString())) {
                continue;
            }
            total++;
            if (FTBChunksAPI.api().isManagerLoaded() && ConflictService.isClaimTeam(server, team)) {
                withClaims++;
            }
        }

        int inDefaultRange = 0;
        for (int index = 1; index <= DEFAULT_COUNT; index++) {
            if (findTestTeamBySlot(manager, index) != null) {
                inDefaultRange++;
            }
        }

        return new CountResult(total, withClaims, inDefaultRange);
    }

    public static List<Team> findAllTestTeams(TeamManager manager) {
        List<Team> testTeams = new ArrayList<>();
        for (Team team : manager.getTeams()) {
            if (isTestTeamName(team.getName().getString())) {
                testTeams.add(team);
            }
        }
        testTeams.sort(Comparator.comparing(team -> team.getName().getString(), String.CASE_INSENSITIVE_ORDER));
        return testTeams;
    }

    @Nullable
    private static Team findTestTeamBySlot(TeamManager manager, int index) {
        String expectedName = testTeamName(index);
        Team byLookup = manager.getTeamByName(expectedName).orElse(null);
        if (byLookup != null) {
            return byLookup;
        }
        for (Team team : manager.getTeams()) {
            if (expectedName.equalsIgnoreCase(team.getName().getString())) {
                return team;
            }
        }
        return null;
    }

    private static boolean isTestTeamName(String name) {
        return name.startsWith(TEAM_PREFIX);
    }

    public static ClearResult clear(MinecraftServer server, CommandSourceStack source, int count) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return new ClearResult(0, 0, 0);
        }

        TeamManager manager = FTBTeamsAPI.api().getManager();
        CommandSourceStack deleteSource = server.createCommandSourceStack().withPermission(4);
        List<Team> testTeams = findAllTestTeams(manager);
        if (count > 0 && testTeams.size() > count) {
            testTeams = new ArrayList<>(testTeams.subList(0, count));
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            Team playerTeam = manager.getTeamForPlayer(player).orElse(null);
            if (playerTeam != null) {
                clearDemoWars(LcClaimEconomySavedData.get(server), playerTeam.getTeamId(), testTeams);
            }
        }

        int deleted = 0;
        int skipped = 0;
        int failed = 0;

        for (Team team : testTeams) {
            if (!(team instanceof ServerTeam serverTeam)) {
                skipped++;
                LcClaimEconomy.LOGGER.warn("Refusing to delete non-server test team {}", team.getName().getString());
                continue;
            }

            try {
                serverTeam.delete(deleteSource);
                deleted++;
            } catch (Exception exception) {
                failed++;
                LcClaimEconomy.LOGGER.warn("Failed to delete test team {}", team.getName().getString(), exception);
            }
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            ConflictSyncCoordinator.syncToPlayer(player);
        }

        return new ClearResult(deleted, skipped, failed);
    }

    private static String testTeamName(int index) {
        return TEAM_PREFIX + String.format("%02d", index);
    }

    public static SeedResult seed(MinecraftServer server, CommandSourceStack source, int count) throws CommandSyntaxException {
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return new SeedResult(0, 0, 0, 0, 0, 0);
        }

        TeamManager manager = FTBTeamsAPI.api().getManager();
        ResourceKey<Level> dimension = source.getEntity() != null
                ? source.getEntity().level().dimension()
                : Level.OVERWORLD;
        CommandSourceStack claimSource = server.createCommandSourceStack().withPermission(4);

        int created = 0;
        int skipped = 0;
        int failed = 0;

        for (int index = 1; index <= count; index++) {
            String name = testTeamName(index);
            Team team = findTestTeamBySlot(manager, index);
            if (team != null) {
                ChunkTeamData existingClaims = FTBChunksAPI.api().getManager().getOrCreateData(team);
                if (!existingClaims.getClaimedChunks().isEmpty()) {
                    skipped++;
                    continue;
                }
            } else {
                team = manager.createServerTeam(
                        source,
                        name,
                        "Dev test team for war UI",
                        TEAM_COLORS[(index - 1) % TEAM_COLORS.length]
                );
            }

            ChunkDimPos chunkPos = new ChunkDimPos(dimension, CHUNK_BASE_X + index, CHUNK_BASE_Z);
            ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
            ClaimResult claimResult = chunkData.claim(claimSource, chunkPos, false);
            if (!claimResult.isSuccess()) {
                failed++;
                LcClaimEconomy.LOGGER.warn("Failed to claim chunk for {} at {}: {}", name, chunkPos, claimResult.getResultId());
                continue;
            }

            created++;
        }

        WarSeedResult warResult = seedDemoWars(server, source.getEntity() instanceof ServerPlayer player ? player : null, count);
        if (source.getEntity() instanceof ServerPlayer player) {
            ConflictSyncCoordinator.syncToPlayer(player);
        }

        return new SeedResult(
                created,
                skipped,
                failed,
                warResult.incomingWars(),
                warResult.outgoingWars(),
                warResult.availableTargets()
        );
    }

    private record WarSeedResult(int incomingWars, int outgoingWars, int availableTargets) {
    }

    private static WarSeedResult seedDemoWars(MinecraftServer server, @Nullable ServerPlayer player, int count) {
        if (!ConflictService.isEnabled()) {
            return new WarSeedResult(0, 0, 0);
        }
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return new WarSeedResult(0, 0, 0);
        }

        Team playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (playerTeam == null || !ConflictService.isClaimTeam(server, playerTeam)) {
            LcClaimEconomy.LOGGER.info("Skipped demo war seeding: {} has no claimed chunks", player.getGameProfile().getName());
            return new WarSeedResult(0, 0, 0);
        }

        TeamManager manager = FTBTeamsAPI.api().getManager();
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID playerTeamId = playerTeam.getTeamId();

        List<Team> testTeams = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            Team testTeam = findTestTeamBySlot(manager, index);
            if (testTeam != null && ConflictService.isClaimTeam(server, testTeam)) {
                testTeams.add(testTeam);
            }
        }
        if (testTeams.isEmpty()) {
            for (Team testTeam : findAllTestTeams(manager)) {
                if (ConflictService.isClaimTeam(server, testTeam)) {
                    testTeams.add(testTeam);
                }
            }
        }

        clearDemoWars(savedData, playerTeamId, testTeams);

        int incomingCount = Math.min(DEMO_INCOMING_WARS, testTeams.size());
        int outgoingCount = Math.min(DEMO_OUTGOING_WARS, Math.max(0, testTeams.size() - incomingCount));

        for (int i = 0; i < incomingCount; i++) {
            savedData.setWarTarget(testTeams.get(i).getTeamId(), playerTeamId, true);
        }

        for (int i = 0; i < outgoingCount; i++) {
            savedData.setWarTarget(playerTeamId, testTeams.get(incomingCount + i).getTeamId(), true);
        }

        int availableTargets = Math.max(0, testTeams.size() - incomingCount - outgoingCount);
        return new WarSeedResult(incomingCount, outgoingCount, availableTargets);
    }

    private static void clearDemoWars(LcClaimEconomySavedData savedData, UUID playerTeamId, List<Team> testTeams) {
        Set<UUID> testTeamIds = new HashSet<>();
        for (Team testTeam : testTeams) {
            testTeamIds.add(testTeam.getTeamId());
        }

        for (UUID targetId : new HashSet<>(savedData.getWarTargets(playerTeamId))) {
            if (testTeamIds.contains(targetId)) {
                savedData.setWarTarget(playerTeamId, targetId, false);
            }
        }

        for (Team testTeam : testTeams) {
            UUID testTeamId = testTeam.getTeamId();
            if (savedData.isAtWarWith(testTeamId, playerTeamId)) {
                savedData.setWarTarget(testTeamId, playerTeamId, false);
            }
        }
    }
}
