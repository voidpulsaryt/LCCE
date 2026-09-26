package dev.voidpulsaryt.lcce.integration.ftb;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.voidpulsaryt.lcce.network.RegionSyncPayload;
import dev.voidpulsaryt.lcce.network.RegionSyncPayload.RegionEntry;
import dev.voidpulsaryt.lcce.region.Region;
import dev.voidpulsaryt.lcce.region.RegionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Pushes a team's entire current set of regions to its own online members - the region-data
 * equivalent of {@code TeamBalance}'s own sync, and needed for the same reason: this mod's own
 * {@code RegionManager} is a plain server-side {@code SavedData}, so without this no client would
 * ever know which of its claimed chunks belong to which region (or that region's protection
 * settings), which the World Map's nested region borders and info popup both need.
 * <p>
 * Called after every region mutation ({@code RegionCommand}) and once on login
 * ({@code TeamBalance.init}'s {@code PLAYER_LOGGED_IN} listener triggers this too) - always the
 * whole snapshot, never a diff, since editing regions is a rare admin action.
 */
public final class RegionSync {

    private RegionSync() {}

    public static void init() {
        // A freshly-logged-in player wouldn't otherwise see their team's regions until one next
        // changes - matches TeamBalance's own login sync for the same reason.
        TeamEvent.PLAYER_LOGGED_IN.register(event -> pushTo(event.getPlayer(), event.getTeam()));
    }

    public static void pushToTeam(MinecraftServer server, Team team) {
        List<Region> regions = RegionManager.get(server).getRegionsForTeam(team.getId());
        List<RegionEntry> entries = regions.stream().map(RegionEntry::fromRegion).toList();
        RegionSyncPayload payload = new RegionSyncPayload(team.getId(), entries);
        for (ServerPlayer player : team.getOnlineMembers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void pushTo(ServerPlayer player, Team team) {
        List<Region> regions = RegionManager.get(player.getServer()).getRegionsForTeam(team.getId());
        List<RegionEntry> entries = regions.stream().map(RegionEntry::fromRegion).toList();
        PacketDistributor.sendToPlayer(player, new RegionSyncPayload(team.getId(), entries));
    }
}
