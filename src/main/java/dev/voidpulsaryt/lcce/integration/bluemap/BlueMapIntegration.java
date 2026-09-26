package dev.voidpulsaryt.lcce.integration.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import dev.voidpulsaryt.lcce.marketplace.ChunkListing;
import dev.voidpulsaryt.lcce.marketplace.ChunkOwnership;
import dev.voidpulsaryt.lcce.marketplace.MarketplaceManager;
import dev.voidpulsaryt.lcce.region.Region;
import dev.voidpulsaryt.lcce.region.RegionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/**
 * Draws every team's claims (colored per team), regions (labeled), and marketplace listings
 * (labeled with price) onto BlueMap's web map. Unlike the Xaero's integration, this is entirely
 * server-side - BlueMap renders and serves the map itself, so there's no client component and no
 * networking needed here, just reading the same server-side data every other command reads.
 * <p>
 * BlueMap loads asynchronously and may enable/disable independently of this mod, so markers are
 * (re)built via {@code BlueMapAPI.onEnable}, and refreshed on a simple timer thereafter rather
 * than trying to hook every individual claim/region/listing mutation.
 */
public final class BlueMapIntegration {

    private static final String MARKER_SET_ID = "lcce_claims";
    private static final long REFRESH_INTERVAL_TICKS = 400; // ~20 seconds

    private static volatile boolean enabled = false;
    private static long lastRefresh = -1;

    private BlueMapIntegration() {}

    public static void init(MinecraftServer server) {
        BlueMapAPI.onEnable(api -> {
            enabled = true;
            refresh(server, api);
        });
        BlueMapAPI.onDisable(api -> enabled = false);

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (!enabled) {
                return;
            }
            long now = event.getServer().overworld().getGameTime();
            if (lastRefresh < 0 || now - lastRefresh >= REFRESH_INTERVAL_TICKS) {
                lastRefresh = now;
                BlueMapAPI.getInstance().ifPresent(api -> refresh(event.getServer(), api));
            }
        });
    }

    private static void refresh(MinecraftServer server, BlueMapAPI api) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        RegionManager regionManager = RegionManager.get(server);
        MarketplaceManager marketManager = MarketplaceManager.get(server);

        for (ServerLevel level : server.getAllLevels()) {
            api.getWorld(level).ifPresent(world -> {
                for (BlueMapMap map : world.getMaps()) {
                    MarkerSet markerSet = MarkerSet.builder()
                            .label("LCCE Claims")
                            .toggleable(true)
                            .build();

                    for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
                        for (ClaimedChunk claimed : FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks()) {
                            if (!claimed.getPos().dimension().equals(level.dimension())) {
                                continue;
                            }
                            addClaimMarker(markerSet, team, claimed, regionManager, marketManager);
                        }
                    }

                    map.getMarkerSets().put(MARKER_SET_ID, markerSet);
                }
            });
        }
    }

    private static void addClaimMarker(MarkerSet markerSet, Team team, ClaimedChunk claimed,
                                        RegionManager regionManager, MarketplaceManager marketManager) {
        var pos = claimed.getPos();
        String markerId = pos.dimension().location() + "_" + pos.x() + "_" + pos.z();

        int minX = pos.x() * 16;
        int minZ = pos.z() * 16;
        Shape shape = Shape.createRect(minX, minZ, minX + 16, minZ + 16);

        Color lineColor = colorFor(team.getId());
        Color fillColor = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 0.25f);

        String label = labelFor(team, claimed, regionManager, marketManager);

        ShapeMarker marker = ShapeMarker.builder()
                .label(label)
                .shape(shape, 65f)
                .lineColor(lineColor)
                .fillColor(fillColor)
                .lineWidth(2)
                .depthTestEnabled(false)
                .build();

        markerSet.getMarkers().put(markerId, marker);
    }

    private static String labelFor(Team team, ClaimedChunk claimed, RegionManager regionManager, MarketplaceManager marketManager) {
        StringBuilder label = new StringBuilder(team.getName().getString());

        ChunkOwnership ownership = marketManager.getOwnership(claimed.getPos());
        if (ownership != null && ownership.customLabel() != null) {
            label.append(" - ").append(ownership.customLabel());
        } else {
            Region region = regionManager.getRegionAt(claimed.getPos());
            if (region != null) {
                label.append(" - ").append(region.name());
            }
        }

        ChunkListing listing = marketManager.getListing(claimed.getPos());
        if (listing != null) {
            label.append(" (for sale: ").append(CurrencyBridge.formatValue(listing.price()).getString()).append(")");
        }

        return label.toString();
    }

    /** A stable color per team, derived from its ID so it doesn't drift between sessions. */
    private static Color colorFor(UUID teamId) {
        int hash = teamId.hashCode();
        float hue = (hash & 0xFFFF) / 65536f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.65f, 0.95f);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 1f);
    }
}
