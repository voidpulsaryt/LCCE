package dev.voidpulsaryt.lcce.client.xaero;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket.ChunkChangeOp;
import dev.ftb.mods.ftblibrary.math.XZ;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Appends "Claim/Unclaim/Force-load/Un-force-load Selected" to Xaero's World Map right-click menu
 * for whatever chunks the player has drag-selected ({@link MapTileSelection}, the same rectangle
 * Xaero's own "Export Map as PNG" uses) - added via {@link dev.voidpulsaryt.lcce.client.xaero.mixin.GuiMapMixin}
 * since {@code GuiMap#getRightClickOptions} has no other extension point.
 * <p>
 * Sends FTB Chunks' own {@code RequestChunkChangePacket} rather than a payload of this mod's own -
 * that's the exact packet FTB Chunks' own client already uses for claim/unclaim/force-load, and
 * server-side it calls straight into {@code ChunkTeamData#claim}/{@code unclaim}/{@code forceLoad},
 * the same methods that fire the {@code ClaimedChunkEvent}s this mod's economy already listens to -
 * so a chunk claimed this way is billed exactly like one claimed by any other means, for free.
 */
public final class WorldMapClaimMenu {

    private static final int MAX_CHUNKS_PER_REQUEST = 128;

    private WorldMapClaimMenu() {}

    public static void addOptions(GuiMap screen, ArrayList<RightClickOption> options, MapTileSelection selection) {
        if (selection == null) {
            return;
        }
        Optional<MapDimension> dimOpt = MapDimension.getCurrent();
        if (dimOpt.isEmpty()) {
            return;
        }
        MapDimension dim = dimOpt.get();

        Set<XZ> chunks = collectChunks(selection);
        Set<MapChunk> mapChunks = chunks.stream()
                .map(xz -> dim.getRegion(XZ.regionFromChunk(xz.x(), xz.z())).getChunkForAbsoluteChunkPos(xz))
                .collect(Collectors.toSet());

        if (mapChunks.stream().anyMatch(c -> c.getClaimedDate().isEmpty())) {
            options.add(new RightClickOption("lcce.map.claim_selected", options.size(), screen) {
                @Override
                public void onAction(Screen screen) {
                    send(ChunkChangeOp.CLAIM, chunks);
                }
            });
        }
        if (mapChunks.stream().anyMatch(c -> c.getClaimedDate().isPresent())) {
            options.add(new RightClickOption("lcce.map.unclaim_selected", options.size(), screen) {
                @Override
                public void onAction(Screen screen) {
                    send(ChunkChangeOp.UNCLAIM, chunks);
                }
            });
        }
        if (mapChunks.stream().anyMatch(c -> c.getClaimedDate().isPresent() && c.getForceLoadedDate().isEmpty())) {
            options.add(new RightClickOption("lcce.map.forceload_selected", options.size(), screen) {
                @Override
                public void onAction(Screen screen) {
                    send(ChunkChangeOp.LOAD, chunks);
                }
            });
        }
        if (mapChunks.stream().anyMatch(c -> c.getForceLoadedDate().isPresent())) {
            options.add(new RightClickOption("lcce.map.unforceload_selected", options.size(), screen) {
                @Override
                public void onAction(Screen screen) {
                    send(ChunkChangeOp.UNLOAD, chunks);
                }
            });
        }
        if (mapChunks.stream().anyMatch(c -> c.getClaimedDate().isPresent())) {
            options.add(new RightClickOption("lcce.map.new_region_from_selection", options.size(), screen) {
                @Override
                public void onAction(Screen screen) {
                    Minecraft.getInstance().setScreen(new CreateRegionScreen(screen, chunks));
                }
            });
            options.add(new RightClickOption("lcce.map.sell_country_selection", options.size(), screen) {
                @Override
                public void onAction(Screen screen) {
                    Minecraft.getInstance().setScreen(new SellCountryScreen(screen, chunks));
                }
            });
        }
    }

    private static Set<XZ> collectChunks(MapTileSelection selection) {
        Set<XZ> chunks = new HashSet<>();
        int left = selection.getLeft();
        int top = selection.getTop();
        int right = selection.getRight();
        int bottom = selection.getBottom();
        for (int cx = left; cx <= right; cx++) {
            for (int cz = top; cz <= bottom; cz++) {
                if (chunks.size() >= MAX_CHUNKS_PER_REQUEST) {
                    return chunks;
                }
                chunks.add(XZ.of(cx, cz));
            }
        }
        return chunks;
    }

    private static void send(ChunkChangeOp op, Set<XZ> chunks) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        NetworkManager.sendToServer(new RequestChunkChangePacket(op, chunks, false, Optional.empty()));
    }
}
