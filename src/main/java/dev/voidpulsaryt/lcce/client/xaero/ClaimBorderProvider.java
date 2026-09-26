package dev.voidpulsaryt.lcce.client.xaero;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.voidpulsaryt.lcce.client.ClaimMapCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Supplies claimed chunks near the player, one element per chunk, for each render pass. Filtered
 * to the player's current dimension and a fixed radius so a large claim map doesn't mean iterating
 * every claim on the server every frame.
 */
public final class ClaimBorderProvider extends MinimapElementRenderProvider<ClaimBorderElement, Object> {

    private static final int RADIUS_CHUNKS = 24;

    private Iterator<ClaimBorderElement> current;

    @Override
    public void begin(MinimapElementRenderLocation location, Object context) {
        current = collectNearbyClaims().iterator();
    }

    @Override
    public boolean hasNext(MinimapElementRenderLocation location, Object context) {
        return current != null && current.hasNext();
    }

    @Override
    public ClaimBorderElement getNext(MinimapElementRenderLocation location, Object context) {
        return current.next();
    }

    @Override
    public void end(MinimapElementRenderLocation location, Object context) {
        current = null;
    }

    private static List<ClaimBorderElement> collectNearbyClaims() {
        List<ClaimBorderElement> result = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return result;
        }
        var dimension = player.level().dimension();
        ChunkPos playerChunk = player.chunkPosition();

        for (Map.Entry<ChunkDimPos, java.util.UUID> entry : ClaimMapCache.snapshot().entrySet()) {
            ChunkDimPos pos = entry.getKey();
            if (!pos.dimension().equals(dimension)) {
                continue;
            }
            if (Math.abs(pos.x() - playerChunk.x) > RADIUS_CHUNKS || Math.abs(pos.z() - playerChunk.z) > RADIUS_CHUNKS) {
                continue;
            }
            result.add(new ClaimBorderElement(pos, entry.getValue(), ClaimMapCache.colorFor(entry.getValue())));
        }
        return result;
    }
}
