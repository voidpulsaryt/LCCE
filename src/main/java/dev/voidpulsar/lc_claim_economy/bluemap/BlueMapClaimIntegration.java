package dev.voidpulsar.lc_claim_economy.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankAccountHelper;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.service.WarService;
import dev.voidpulsar.lc_claim_economy.teams.FtbTeamCatalog;
import dev.voidpulsar.lc_claim_economy.util.DurationFormat;
import dev.voidpulsar.lc_claim_economy.util.MoneyMessageUtil;
import dev.voidpulsar.lc_claim_economy.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Optional BlueMap integration: draws each tracked FTB team's claimed chunks as colored area
 * markers on the live map, refreshed periodically, and clears them again once BlueMap or a
 * team's claims go away. Only ever constructed/registered when both {@code ModCompat.isBlueMapAvailable()}
 * and {@code ModCompat.isFtbAvailable()} are true (see that class's javadoc for why merely
 * classloading this file is unsafe otherwise) - there is no OP&C claim overlay yet.
 * <p>
 * BlueMap itself may enable/disable independently of the Minecraft server lifecycle (e.g. on
 * a config reload), so {@code onEnable}/{@code onDisable} and the server start/stop events are
 * tracked separately; a refresh only actually runs once both an API instance and a server
 * reference are known.
 */
public final class BlueMapClaimIntegration {
    private static final String MARKER_SET_PREFIX = "lc_claim_economy-team-";
    private static final int REFRESH_INTERVAL_TICKS = 200; // ~10s

    @Nullable
    private volatile BlueMapAPI api;
    @Nullable
    private volatile MinecraftServer server;
    private int tickCounter = 0;

    /** Maps this integration has written a given team's marker set onto, as of the last refresh - used to clean up stale markers when a team loses claims in a dimension or is no longer tracked at all. */
    private final Map<UUID, Set<BlueMapMap>> lastMapsByTeam = new HashMap<>();

    public BlueMapClaimIntegration() {
        BlueMapAPI.onEnable(enabled -> {
            this.api = enabled;
            refresh();
        });
        BlueMapAPI.onDisable(disabled -> {
            this.api = null;
            lastMapsByTeam.clear();
        });
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        this.server = event.getServer();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        this.server = null;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (api == null || server == null) {
            return;
        }
        if (++tickCounter < REFRESH_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        refresh();
    }

    private void refresh() {
        BlueMapAPI currentApi = this.api;
        MinecraftServer currentServer = this.server;
        if (currentApi == null || currentServer == null) {
            return;
        }

        if (!LcClaimEconomyConfig.SERVER.blueMapClaimOverlaysEnabled.get()) {
            clearAllTeamMarkers();
            return;
        }

        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }

        Set<UUID> currentTeamIds = new HashSet<>();
        for (Team team : FtbTeamCatalog.trackedTeams(currentServer)) {
            try {
                currentTeamIds.add(team.getTeamId());
                refreshTeam(currentApi, currentServer, team);
            } catch (Exception e) {
                LcClaimEconomy.LOGGER.error("Failed to refresh BlueMap markers for team {}", team.getId(), e);
            }
        }

        // Teams that disbanded or otherwise dropped out of tracking entirely since the last
        // refresh: their claims are gone, but refreshTeam() above never ran for them this
        // cycle, so their old marker sets would otherwise linger forever.
        for (UUID staleTeamId : new ArrayList<>(lastMapsByTeam.keySet())) {
            if (!currentTeamIds.contains(staleTeamId)) {
                removeTeamMarkerSet(staleTeamId, lastMapsByTeam.remove(staleTeamId));
            }
        }
    }

    private void refreshTeam(BlueMapAPI api, MinecraftServer server, Team team) {
        UUID teamId = team.getTeamId();
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);

        Map<ResourceKey<Level>, List<ClaimedChunk>> byDimension = new HashMap<>();
        for (ClaimedChunk chunk : chunkData.getClaimedChunks()) {
            byDimension.computeIfAbsent(chunk.getPos().dimension(), d -> new ArrayList<>()).add(chunk);
        }

        Set<BlueMapMap> touchedMaps = new HashSet<>();
        if (!byDimension.isEmpty()) {
            String detail = buildDetail(server, team);
            int[] rgb = teamColorRgb(teamId);

            for (Map.Entry<ResourceKey<Level>, List<ClaimedChunk>> entry : byDimension.entrySet()) {
                ServerLevel level = server.getLevel(entry.getKey());
                if (level == null) {
                    continue;
                }
                Optional<BlueMapWorld> world = api.getWorld(level);
                if (world.isEmpty()) {
                    continue;
                }

                MarkerSet markerSet = buildMarkerSet(team, entry.getValue(), rgb, detail);
                for (BlueMapMap map : world.get().getMaps()) {
                    map.getMarkerSets().put(MARKER_SET_PREFIX + teamId, markerSet);
                    touchedMaps.add(map);
                }
            }
        }

        Set<BlueMapMap> previousMaps = lastMapsByTeam.put(teamId, touchedMaps);
        if (previousMaps != null) {
            for (BlueMapMap staleMap : previousMaps) {
                if (!touchedMaps.contains(staleMap)) {
                    staleMap.getMarkerSets().remove(MARKER_SET_PREFIX + teamId);
                }
            }
        }
    }

    private static MarkerSet buildMarkerSet(Team team, List<ClaimedChunk> chunks, int[] rgb, String detail) {
        MarkerSet markerSet = MarkerSet.builder()
                .label(WarService.displayName(team))
                .defaultHidden(false)
                .build();

        Color fillColor = new Color(rgb[0], rgb[1], rgb[2], 0.35f);
        Color lineColor = new Color(rgb[0], rgb[1], rgb[2], 0.9f);
        int index = 0;
        for (ClaimedChunk chunk : chunks) {
            int minX = chunk.getPos().x() * 16;
            int minZ = chunk.getPos().z() * 16;
            ShapeMarker marker = ShapeMarker.builder()
                    .label(WarService.displayName(team))
                    .detail(detail)
                    .shape(Shape.createRect(minX, minZ, minX + 16, minZ + 16), 65f)
                    .fillColor(fillColor)
                    .lineColor(lineColor)
                    .lineWidth(2)
                    .depthTestEnabled(false)
                    .centerPosition()
                    .build();
            markerSet.put("chunk-" + (index++), marker);
        }
        return markerSet;
    }

    private static String buildDetail(MinecraftServer server, Team team) {
        BankAccountHelper.ensurePartyAccountExists(server, team);
        IBankAccount account = BankAccountHelper.getAccountForTeam(server, team);
        long balanceCopper = account == null ? 0L : MoneyUtil.totalCopper(account);

        long gameTime = server.overworld().getGameTime();
        long nextUpkeepTick = LcClaimEconomySavedData.get(server).getNextUpkeepTick(team.getTeamId());
        String nextCharge = nextUpkeepTick < 0L
                ? "unknown"
                : DurationFormat.ticksToShortString(Math.max(0L, nextUpkeepTick - gameTime));

        return "<div><strong>" + escapeHtml(WarService.displayName(team)) + "</strong><br>"
                + "Balance: " + escapeHtml(MoneyMessageUtil.formatPrice(balanceCopper).getString()) + "<br>"
                + "Next upkeep charge: " + escapeHtml(nextCharge) + "</div>";
    }

    private static int[] teamColorRgb(UUID teamId) {
        float hue = (Math.abs(teamId.hashCode()) % 360) / 360f;
        int packed = java.awt.Color.HSBtoRGB(hue, 0.65f, 0.9f);
        return new int[] { (packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF };
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void clearAllTeamMarkers() {
        for (Map.Entry<UUID, Set<BlueMapMap>> entry : lastMapsByTeam.entrySet()) {
            removeTeamMarkerSet(entry.getKey(), entry.getValue());
        }
        lastMapsByTeam.clear();
    }

    private static void removeTeamMarkerSet(UUID teamId, @Nullable Set<BlueMapMap> maps) {
        if (maps == null) {
            return;
        }
        for (BlueMapMap map : maps) {
            map.getMarkerSets().remove(MARKER_SET_PREFIX + teamId);
        }
    }
}
