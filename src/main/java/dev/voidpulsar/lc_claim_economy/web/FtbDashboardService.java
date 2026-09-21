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
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.bluemap.BlueMapDashboardLink;
import dev.voidpulsar.lc_claim_economy.compat.ModCompat;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.ChunkCoordKey;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.service.LandChunkService;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPricing;
import dev.voidpulsar.lc_claim_economy.service.WarDeclarationWindow;
import dev.voidpulsar.lc_claim_economy.service.ConflictService;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
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
 * All of the FTB-Chunks-specific work behind the dashboard API - resolving a
 * player's team, reading/writing its protection properties, and assembling
 * the big dashboard JSON blob. Kept behind {@link DashboardApi}'s
 * {@code ModCompat.isFtbAvailable()} check for the same class-boundary reason
 * documented on {@link WebDataService}: this file is free to import
 * {@code dev.ftb.mods.*} types precisely because nothing outside the web
 * package ever calls it directly.
 */
final class FtbDashboardService {
    private FtbDashboardService() {
    }

    private record ProtectionMeta(String name, String desc, long priceCopper) {
    }

    /**
     * The protection toggles shown on the dashboard, in display order. Built fresh on
     * every call (rather than cached) so a price changed in the config screen shows up
     * immediately without needing a server restart - these lists are tiny, so the cost
     * of rebuilding is negligible next to that.
     */
    private static Map<TeamProperty<?>, ProtectionMeta> protectionCatalog() {
        var prices = LcClaimEconomyConfig.SERVER;
        Map<TeamProperty<?>, ProtectionMeta> catalog = new LinkedHashMap<>();
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ALLOW_PVP,
                new ProtectionMeta("Disable PvP", "Prevents PvP combat inside your claims.", prices.pvpDisablePrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ALLOW_EXPLOSIONS,
                new ProtectionMeta("Explosion protection", "Blocks explosion damage to claimed terrain.", prices.explosionProtectionPrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ALLOW_MOB_GRIEFING,
                new ProtectionMeta("Mob-grief protection", "Stops mobs from breaking blocks in claims.", prices.mobGriefProtectionPrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.BLOCK_EDIT_MODE,
                new ProtectionMeta("Block edit: private", "Only team members can place/break blocks.", prices.blockEditProtectionPrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.BLOCK_INTERACT_MODE,
                new ProtectionMeta("Block interact: private", "Only team members can use doors, levers, etc.", prices.blockInteractProtectionPrice.get()));
        catalog.put(dev.ftb.mods.ftbchunks.api.FTBChunksProperties.ENTITY_INTERACT_MODE,
                new ProtectionMeta("Entity interact: private", "Only team members can interact with entities.", prices.entityInteractProtectionPrice.get()));
        return catalog;
    }

    // FTBChunksProperties mixes two representations for what's conceptually a single on/off
    // switch: some are plain booleans (ALLOW_*, where false means "protection is on"), others
    // are PrivacyMode (where anything but PUBLIC means "protection is on"). The dashboard only
    // ever shows a single toggle, so this normalizes both down to that one boolean.
    private static boolean isActive(Team team, TeamProperty<?> property, Object liveValue) {
        if (liveValue instanceof Boolean allowed) {
            return !allowed;
        }
        if (liveValue instanceof PrivacyMode mode) {
            return mode != PrivacyMode.PUBLIC;
        }
        return false;
    }

    /** Inverse of {@link #isActive} - the raw property value to set when the dashboard switch is toggled to {@code active}. */
    @SuppressWarnings("unchecked")
    private static <T> void applyActive(Team team, TeamProperty<T> property, boolean active) {
        T currentValue = team.getProperty(property);
        if (currentValue instanceof Boolean) {
            team.setProperty(property, (T) Boolean.valueOf(!active));
        } else if (currentValue instanceof PrivacyMode) {
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
        TeamQueuedChanges pendingState = savedData.getPendingState(team.getTeamId());
        BankLedgerAccess.ensurePartyAccountExists(server, team);
        IBankAccount account = BankLedgerAccess.getAccountForTeam(server, team);
        long balanceCopper = CurrencyAmounts.totalCopper(account);

        JsonWriter playerJson = JsonWriter.object()
                .field("name", playerName(server, playerId))
                .field("uuid", playerId.toString());

        JsonWriter teamJson = JsonWriter.object()
                .field("name", ConflictService.displayName(team))
                .field("isParty", team.isPartyTeam())
                .field("rank", team.getRankForPlayer(playerId).name())
                .field("peaceful", ConflictService.isPeaceful(server, team.getTeamId()));

        ConflictService.WarCostBreakdown costs = ConflictService.calculateWarCosts(server, team, pendingState);
        ChunkTeamData chunkData = FTBChunksAPI.api().isManagerLoaded()
                ? FTBChunksAPI.api().getManager().getOrCreateData(team)
                : null;
        int effectiveForceLoads = chunkData == null ? 0 : SafeguardPricing.countEffectiveForceLoads(chunkData, pendingState);
        long forceLoadCopper = SafeguardPricing.calculateForceLoadCopper(effectiveForceLoads);
        long totalCopper = costs.totalUpkeepCopper() + forceLoadCopper;
        boolean canAfford = ConflictService.canAffordUpkeep(server, team, pendingState, account);

        // -1 is the sentinel for "no upkeep scheduled yet" (e.g. team just formed); the client
        // treats a negative value as "don't show a countdown" rather than a wall-clock time.
        // Otherwise convert the remaining game ticks to seconds at the fixed 20 ticks/sec rate.
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

    private static JsonWriter buildLandJson(ChunkTeamData chunkData, TeamQueuedChanges pendingState) {
        List<JsonWriter> entries = new java.util.ArrayList<>();
        if (chunkData != null) {
            for (ClaimedChunk chunk : chunkData.getClaimedChunks()) {
                String key = ChunkCoordKey.encode(chunk.getPos());
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

    private static List<JsonWriter> buildProtectionEntries(Team team, TeamQueuedChanges pendingState) {
        List<JsonWriter> rows = new java.util.ArrayList<>();
        for (var catalogEntry : protectionCatalog().entrySet()) {
            TeamProperty<?> property = catalogEntry.getKey();
            ProtectionMeta meta = catalogEntry.getValue();
            Object liveValue = team.getProperty(property);
            boolean active = isActive(team, property, liveValue);

            String key = SafeguardPricing.propertyKey(property);
            String pendingRaw = pendingState.pendingProperties().get(key);
            JsonWriter row = JsonWriter.object()
                    .field("key", key)
                    .field("name", meta.name())
                    .field("desc", meta.desc())
                    .field("priceCopper", meta.priceCopper())
                    .field("active", active);
            if (pendingRaw != null) {
                boolean pendingActive = pendingValueIsActive(property, pendingRaw, liveValue);
                row.field("pendingValue", pendingActive ? "on (next period)" : "off (next period)");
            }
            rows.add(row);
        }
        return rows;
    }

    // The unchecked cast is safe here: liveValue always came from team.getProperty(property)
    // for this same TeamProperty<T>, so it's guaranteed to already be a T - deserializePropertyValue
    // only needs it typed as T to have something to fall back to if pendingRaw fails to parse.
    private static <T> boolean pendingValueIsActive(TeamProperty<T> property, String pendingRaw, Object liveValue) {
        @SuppressWarnings("unchecked")
        T fallbackValue = (T) liveValue;
        T resolvedPendingValue = SafeguardPricing.deserializePropertyValue(property, pendingRaw, fallbackValue);
        return isActive(null, property, resolvedPendingValue);
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
        List<JsonWriter> incoming = ConflictService.buildIncomingViews(server, team).stream()
                .map(v -> JsonWriter.object()
                        .field("teamId", v.teamId().toString())
                        .field("name", v.displayName())
                        .field("costCopper", v.warCostCopper())
                        .field("pending", v.status() == dev.voidpulsar.lc_claim_economy.network.ConflictEntryStatus.DECLARE_QUEUED))
                .toList();
        List<JsonWriter> outgoing = ConflictService.buildOutgoingViews(server, team).stream()
                .map(v -> JsonWriter.object()
                        .field("teamId", v.teamId().toString())
                        .field("name", v.displayName())
                        .field("costCopper", v.warCostCopper())
                        .field("pending", v.status() != dev.voidpulsar.lc_claim_economy.network.ConflictEntryStatus.ENGAGED))
                .toList();
        List<JsonWriter> available = ConflictService.buildAvailableTargets(server, team).stream()
                .map(v -> JsonWriter.object()
                        .field("teamId", v.teamId().toString())
                        .field("name", v.displayName())
                        .field("costCopper", v.warCostCopper()))
                .toList();

        return JsonWriter.object()
                .field("enabled", ConflictService.isEnabled())
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
        if (!BankLedgerAccess.canPurchaseForTeam(team, playerId)) {
            return ActionResult.failure("Only team owners and officers can manage protections.");
        }
        TeamProperty<?> property = protectionCatalog().keySet().stream()
                .filter(p -> SafeguardPricing.propertyKey(p).equals(propertyKey))
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
        if (!BankLedgerAccess.canPurchaseForTeam(team, playerId)) {
            return ActionResult.failure("Only team owners and officers can manage this.");
        }
        boolean applied = ConflictService.setPeaceful(server, team.getTeamId(), peaceful);
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
                ? chunkData.forceLoad(source, ChunkCoordKey.toChunkDimPos(chunkKey), false)
                : chunkData.unForceLoad(source, ChunkCoordKey.toChunkDimPos(chunkKey), false);
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
        var result = chunkData.unclaim(source, ChunkCoordKey.toChunkDimPos(chunkKey), false);
        return result.isSuccess() ? ActionResult.success("Unclaimed.") : ActionResult.failure("Could not unclaim that chunk.");
    }

    static ActionResult toggleWar(MinecraftServer server, UUID playerId, UUID targetTeamId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return ActionResult.failure("You must be online in-game to manage wars.");
        }
        var message = ConflictService.toggleWar(server, player, targetTeamId);
        return message == null ? ActionResult.success("Updated.") : ActionResult.success(message.getString());
    }
}
