package dev.voidpulsar.lc_claim_economy.web;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsar.lc_claim_economy.bank.BankAccountHelper;
import dev.voidpulsar.lc_claim_economy.bluemap.BlueMapDashboardLink;
import dev.voidpulsar.lc_claim_economy.compat.ModCompat;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.ChunkPosKey;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamPendingState;
import dev.voidpulsar.lc_claim_economy.service.LandChunkService;
import dev.voidpulsar.lc_claim_economy.service.ProtectionPricing;
import dev.voidpulsar.lc_claim_economy.service.WarDeclarationWindow;
import dev.voidpulsar.lc_claim_economy.service.WarService;
import dev.voidpulsar.lc_claim_economy.teams.FtbTeamCatalog;
import dev.voidpulsar.lc_claim_economy.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Only ever called from {@link DashboardApi} behind {@code ModCompat.isFtbAvailable()} -
 * see {@link WebDataService}'s javadoc for why that separation matters. FTB
 * Chunks/Teams only; there is no Open Parties and Claims dashboard backend.
 */
final class FtbDashboardService {
    private FtbDashboardService() {
    }

    private record ProtectionMeta(String name, String desc, long priceCopper) {
    }

    /** Order and metadata for the protection toggles shown on the dashboard. */
    private static Map<TeamProperty<?>, ProtectionMeta> protectionCatalog() {
        var config = LcClaimEconomyConfig.SERVER;
        Map<TeamProperty<?>, ProtectionMeta> catalog = new LinkedHashMap<>();
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ALLOW_PVP,
                new ProtectionMeta("Disable PvP", "Prevents PvP combat inside your claims.", config.pvpDisablePrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ALLOW_EXPLOSIONS,
                new ProtectionMeta("Explosion protection", "Blocks explosion damage to claimed terrain.", config.explosionProtectionPrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ALLOW_MOB_GRIEFING,
                new ProtectionMeta("Mob-grief protection", "Stops mobs from breaking blocks in claims.", config.mobGriefProtectionPrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.BLOCK_EDIT_MODE,
                new ProtectionMeta("Block edit: private", "Only team members can place/break blocks.", config.blockEditProtectionPrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.BLOCK_INTERACT_MODE,
                new ProtectionMeta("Block interact: private", "Only team members can use doors, levers, etc.", config.blockInteractProtectionPrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ENTITY_INTERACT_MODE,
                new ProtectionMeta("Entity interact: private", "Only team members can interact with entities.", config.entityInteractProtectionPrice.get()));
        return catalog;
    }

    /** Whether the given property's live value counts as "protected" (switch shown on). */
    private static boolean isActive(Team team, TeamProperty<?> property, Object liveValue) {
        if (liveValue instanceof Boolean b) {
            return !b; // ALLOW_* booleans: false = protected
        }
        if (liveValue instanceof PrivacyMode mode) {
            return mode != PrivacyMode.PUBLIC;
        }
        return false;
    }

    /** Inverse of {@link #isActive} - the raw property value to set when the dashboard switch is toggled to {@code active}. */
    @SuppressWarnings("unchecked")
    private static <T> void applyActive(Team team, TeamProperty<T> property, boolean active) {
        T current = team.getProperty(property);
        if (current instanceof Boolean) {
            team.setProperty(property, (T) Boolean.valueOf(!active));
        } else if (current instanceof PrivacyMode) {
            team.setProperty(property, (T) (active ? PrivacyMode.PRIVATE : PrivacyMode.PUBLIC));
        }
    }

    static String playerName(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            Optional<GameProfile> cached = server.getProfileCache().get(playerId);
            if (cached.isPresent()) {
                return cached.get().getName();
            }
        }
        return playerId.toString().substring(0, 8);
    }

    private static Optional<Team> resolveTeam(MinecraftServer server, UUID playerId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Optional.empty();
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerId);
    }

    static String buildDashboardJson(MinecraftServer server, UUID playerId) {
        Team team = resolveTeam(server, playerId).orElse(null);
        if (team == null) {
            return null;
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());
        BankAccountHelper.ensurePartyAccountExists(server, team);
        IBankAccount account = BankAccountHelper.getAccountForTeam(server, team);
        long balanceCopper = MoneyUtil.totalCopper(account);

        JsonWriter playerJson = JsonWriter.object()
                .field("name", playerName(server, playerId))
                .field("uuid", playerId.toString());

        JsonWriter teamJson = JsonWriter.object()
                .field("name", WarService.displayName(team))
                .field("isParty", team.isPartyTeam())
                .field("rank", team.getRankForPlayer(playerId).name())
                .field("peaceful", WarService.isPeaceful(server, team.getTeamId()));

        WarService.WarCostBreakdown costs = WarService.calculateWarCosts(server, team, pendingState);
        ChunkTeamData chunkData = FTBChunksAPI.api().isManagerLoaded()
                ? FTBChunksAPI.api().getManager().getOrCreateData(team)
                : null;
        int effectiveForceLoads = chunkData == null ? 0 : ProtectionPricing.countEffectiveForceLoads(chunkData, pendingState);
        long forceLoadCopper = ProtectionPricing.calculateForceLoadCopper(effectiveForceLoads);
        long totalCopper = costs.totalUpkeepCopper() + forceLoadCopper;
        boolean canAfford = WarService.canAffordUpkeep(server, team, pendingState, account);

        long nextUpkeepTick = savedData.getNextUpkeepTick(team.getTeamId());
        long gameTime = server.overworld().getGameTime();
        long secondsUntilNextCharge = nextUpkeepTick < 0L ? -1L : Math.max(0L, (nextUpkeepTick - gameTime) / 20L);

        List<JsonWriter> upkeepLines = new java.util.ArrayList<>();
        upkeepLines.add(upkeepLine("Base protection upkeep", costs.baseUpkeepCopper()));
        if (forceLoadCopper > 0) {
            upkeepLines.add(upkeepLine("Force-loaded chunks (" + effectiveForceLoads + ")", forceLoadCopper));
        }
        if (costs.incomingWarCopper() > 0) {
            upkeepLines.add(upkeepLine("Incoming wars (" + costs.incomingWarCount() + ")", costs.incomingWarCopper()));
        }
        if (costs.outgoingWarCopper() > 0) {
            upkeepLines.add(upkeepLine("Outgoing wars (" + costs.outgoingWarCount() + ")", costs.outgoingWarCopper()));
        }

        JsonWriter upkeepJson = JsonWriter.object()
                .arrayField("lines", upkeepLines)
                .field("totalCopper", totalCopper)
                .field("periodMinutes", LcClaimEconomyConfig.SERVER.upkeepPeriodMinutes.get())
                .field("canAfford", canAfford)
                .field("secondsUntilNextCharge", secondsUntilNextCharge);

        JsonWriter landJson = buildLandJson(chunkData, pendingState);
        JsonWriter protectionsJson = null; // placeholder replaced below (JsonWriter has no array-of-objects-at-root helper)
        List<JsonWriter> protectionEntries = buildProtectionEntries(team, pendingState);
        List<JsonWriter> rosterEntries = buildRosterEntries(server, team);
        JsonWriter warsJson = buildWarsJson(server, team);

        String blueMapWebUrl = ModCompat.isBlueMapAvailable() ? BlueMapDashboardLink.baseUrl() : null;
        String blueMapClaimUrl = ModCompat.isBlueMapAvailable() ? BlueMapDashboardLink.resolve(server, team) : null;
        JsonWriter mapJson = JsonWriter.object()
                .field("webUrl", blueMapWebUrl == null ? "" : blueMapWebUrl)
                .field("claimUrl", blueMapClaimUrl == null ? "" : blueMapClaimUrl);

        return JsonWriter.object()
                .field("player", playerJson)
                .field("team", teamJson)
                .field("balanceCopper", balanceCopper)
                .field("upkeep", upkeepJson)
                .field("map", mapJson)
                .field("land", landJson)
                .arrayField("protections", protectionEntries)
                .field("wars", warsJson)
                .arrayField("roster", rosterEntries)
                .build();
    }

    private static JsonWriter upkeepLine(String label, long copper) {
        return JsonWriter.object().field("label", label).field("copper", copper);
    }

    private static JsonWriter buildLandJson(ChunkTeamData chunkData, TeamPendingState pendingState) {
        List<JsonWriter> entries = new java.util.ArrayList<>();
        if (chunkData != null) {
            for (ClaimedChunk chunk : chunkData.getClaimedChunks()) {
                String key = ChunkPosKey.encode(chunk.getPos());
                String pending = pendingState.isPendingForceLoad(key) ? "forceload"
                        : pendingState.isPendingForceUnload(key) ? "unload"
                        : null;
                JsonWriter entry = JsonWriter.object()
                        .field("key", key)
                        .field("pos", chunk.getPos().x() + ", " + chunk.getPos().z())
                        .field("dim", chunk.getPos().dimension().location().getPath())
                        .field("type", LandChunkService.isLandChunk(chunk) ? "land" : "build")
                        .field("forceLoaded", chunk.isForceLoaded());
                if (pending != null) {
                    entry.field("pending", pending);
                }
                entries.add(entry);
            }
        }
        return JsonWriter.object()
                .field("freeChunks", LcClaimEconomyConfig.SERVER.freeChunks.get())
                .arrayField("entries", entries);
    }

    private static List<JsonWriter> buildProtectionEntries(Team team, TeamPendingState pendingState) {
        List<JsonWriter> entries = new java.util.ArrayList<>();
        for (var entry : protectionCatalog().entrySet()) {
            TeamProperty<?> property = entry.getKey();
            ProtectionMeta meta = entry.getValue();
            Object liveValue = team.getProperty(property);
            boolean active = isActive(team, property, liveValue);

            String key = ProtectionPricing.propertyKey(property);
            String pendingRaw = pendingState.pendingProperties().get(key);
            JsonWriter json = JsonWriter.object()
                    .field("key", key)
                    .field("name", meta.name())
                    .field("desc", meta.desc())
                    .field("priceCopper", meta.priceCopper())
                    .field("active", active);
            if (pendingRaw != null) {
                boolean pendingActive = pendingValueIsActive(property, pendingRaw, liveValue);
                json.field("pendingValue", pendingActive ? "on (next period)" : "off (next period)");
            }
            entries.add(json);
        }
        return entries;
    }

    private static <T> boolean pendingValueIsActive(TeamProperty<T> property, String pendingRaw, Object liveValue) {
        @SuppressWarnings("unchecked")
        T fallback = (T) liveValue;
        T deserialized = ProtectionPricing.deserializePropertyValue(property, pendingRaw, fallback);
        return isActive(null, property, deserialized);
    }

    private static List<JsonWriter> buildRosterEntries(MinecraftServer server, Team team) {
        List<JsonWriter> entries = new java.util.ArrayList<>();
        for (UUID memberId : team.getMembers()) {
            TeamRank rank = team.getRankForPlayer(memberId);
            boolean online = server.getPlayerList().getPlayer(memberId) != null;
            entries.add(JsonWriter.object()
                    .field("name", playerName(server, memberId))
                    .field("rank", rank.name())
                    .field("online", online));
        }
        return entries;
    }

    private static JsonWriter buildWarsJson(MinecraftServer server, Team team) {
        List<JsonWriter> incoming = WarService.buildIncomingViews(server, team).stream()
                .map(v -> JsonWriter.object()
                        .field("teamId", v.teamId().toString())
                        .field("name", v.displayName())
                        .field("costCopper", v.warCostCopper())
                        .field("pending", v.status() == dev.voidpulsar.lc_claim_economy.network.WarEntryStatus.PENDING_DECLARE))
                .toList();
        List<JsonWriter> outgoing = WarService.buildOutgoingViews(server, team).stream()
                .map(v -> JsonWriter.object()
                        .field("teamId", v.teamId().toString())
                        .field("name", v.displayName())
                        .field("costCopper", v.warCostCopper())
                        .field("pending", v.status() != dev.voidpulsar.lc_claim_economy.network.WarEntryStatus.ACTIVE))
                .toList();
        List<JsonWriter> available = WarService.buildAvailableTargets(server, team).stream()
                .map(v -> JsonWriter.object()
                        .field("teamId", v.teamId().toString())
                        .field("name", v.displayName())
                        .field("costCopper", v.warCostCopper()))
                .toList();

        return JsonWriter.object()
                .field("enabled", WarService.isEnabled())
                .field("windowOpen", WarDeclarationWindow.isOpenNow())
                .field("windowDescription", WarDeclarationWindow.isEnabled() ? WarDeclarationWindow.describeWindow() : "")
                .arrayField("incoming", incoming)
                .arrayField("outgoing", outgoing)
                .arrayField("available", available);
    }

    // ---------------- Mutating actions ----------------

    static ActionResult applyProtection(MinecraftServer server, UUID playerId, String propertyKey, boolean active) {
        Team team = resolveTeam(server, playerId).orElse(null);
        if (team == null) {
            return ActionResult.failure("No team found.");
        }
        if (!BankAccountHelper.canPurchaseForTeam(team, playerId)) {
            return ActionResult.failure("Only team owners and officers can manage protections.");
        }
        TeamProperty<?> property = protectionCatalog().keySet().stream()
                .filter(p -> ProtectionPricing.propertyKey(p).equals(propertyKey))
                .findFirst()
                .orElse(null);
        if (property == null) {
            return ActionResult.failure("Unknown protection.");
        }
        applyActive(team, property, active);
        return ActionResult.success("Updated.");
    }

    static ActionResult setPeaceful(MinecraftServer server, UUID playerId, boolean peaceful) {
        Team team = resolveTeam(server, playerId).orElse(null);
        if (team == null) {
            return ActionResult.failure("No team found.");
        }
        if (!BankAccountHelper.canPurchaseForTeam(team, playerId)) {
            return ActionResult.failure("Only team owners and officers can manage this.");
        }
        boolean applied = WarService.setPeaceful(server, team.getTeamId(), peaceful);
        if (!applied) {
            return ActionResult.failure("Cannot become peaceful while your team has an active war.");
        }
        return ActionResult.success("Updated.");
    }

    static ActionResult toggleForceLoad(MinecraftServer server, UUID playerId, String chunkKey, boolean load) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return ActionResult.failure("You must be online in-game to change force-loading.");
        }
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return ActionResult.failure("FTB Chunks is not available.");
        }
        Team team = resolveTeam(server, playerId).orElse(null);
        if (team == null) {
            return ActionResult.failure("No team found.");
        }
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        CommandSourceStack source = player.createCommandSourceStack();
        var result = load
                ? chunkData.forceLoad(source, ChunkPosKey.toChunkDimPos(chunkKey), false)
                : chunkData.unForceLoad(source, ChunkPosKey.toChunkDimPos(chunkKey), false);
        return result.isSuccess() ? ActionResult.success("Updated.") : ActionResult.failure("Could not update force-load state.");
    }

    static ActionResult unclaimChunk(MinecraftServer server, UUID playerId, String chunkKey) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return ActionResult.failure("You must be online in-game to unclaim land.");
        }
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return ActionResult.failure("FTB Chunks is not available.");
        }
        Team team = resolveTeam(server, playerId).orElse(null);
        if (team == null) {
            return ActionResult.failure("No team found.");
        }
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        CommandSourceStack source = player.createCommandSourceStack();
        var result = chunkData.unclaim(source, ChunkPosKey.toChunkDimPos(chunkKey), false);
        return result.isSuccess() ? ActionResult.success("Unclaimed.") : ActionResult.failure("Could not unclaim that chunk.");
    }

    static ActionResult toggleWar(MinecraftServer server, UUID playerId, UUID targetTeamId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return ActionResult.failure("You must be online in-game to manage wars.");
        }
        var message = WarService.toggleWar(server, player, targetTeamId);
        return message == null ? ActionResult.success("Updated.") : ActionResult.success(message.getString());
    }
}
