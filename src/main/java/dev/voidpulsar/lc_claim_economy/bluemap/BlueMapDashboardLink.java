package dev.voidpulsar.lc_claim_economy.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.compat.ModCompat;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Builds a BlueMap web app deep link (the "#mapId:x:y:z:dist:rotation:angle:tilt:ortho:state"
 * hash format the web app itself both reads and writes on every camera move) centered on a
 * team's first claimed chunk, for the web dashboard's Map tab. Kept isolated from {@link
 * BlueMapClaimIntegration} so {@link dev.voidpulsar.lc_claim_economy.web.FtbDashboardService}
 * can call it without pulling in that class's NeoForge event subscriptions - see {@link
 * ModCompat}'s javadoc for why this class's own method signature avoids BlueMap types entirely
 * (only the method body touches them, guarded by {@link ModCompat#isBlueMapAvailable()}).
 */
public final class BlueMapDashboardLink {
    private BlueMapDashboardLink() {
    }

    /** The externally-reachable base URL configured via {@code blueMapWebUrl}, or {@code null} if unset/BlueMap unavailable. */
    @Nullable
    public static String baseUrl() {
        if (!ModCompat.isBlueMapAvailable()) {
            return null;
        }
        String configured = LcClaimEconomyConfig.SERVER.blueMapWebUrl.get();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
    }

    /** A deep link centered on this team's first claimed chunk, or {@code null} if unavailable (no claims, BlueMap not running, etc). */
    @Nullable
    public static String resolve(MinecraftServer server, Team team) {
        String base = baseUrl();
        if (base == null || !FTBChunksAPI.api().isManagerLoaded()) {
            return null;
        }

        BlueMapAPI api = BlueMapAPI.getInstance().orElse(null);
        if (api == null) {
            return null;
        }

        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        ClaimedChunk anyChunk = chunkData.getClaimedChunks().stream().findFirst().orElse(null);
        if (anyChunk == null) {
            return null;
        }

        ServerLevel level = server.getLevel(anyChunk.getPos().dimension());
        if (level == null) {
            return null;
        }
        Optional<BlueMapWorld> world = api.getWorld(level);
        if (world.isEmpty()) {
            return null;
        }
        BlueMapMap map = world.get().getMaps().stream().findFirst().orElse(null);
        if (map == null) {
            return null;
        }

        int blockX = anyChunk.getPos().x() * 16 + 8;
        int blockZ = anyChunk.getPos().z() * 16 + 8;

        // mapId : x : y : z : distance : rotation : angle : tilt : ortho : viewState
        String hash = map.getId() + ":" + blockX + ":100:" + blockZ + ":300:0:0:0:1:flat";
        return base + "/#" + hash;
    }
}
