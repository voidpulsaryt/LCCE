package dev.voidpulsaryt.lcce.integration.ftb;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.voidpulsaryt.lcce.nation.Nation;
import dev.voidpulsaryt.lcce.nation.NationManager;
import dev.voidpulsaryt.lcce.network.NationSyncPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pushes a team's current nation membership (just the nation's name, or an empty string if it
 * isn't in one) to its own online members - the nation-data equivalent of {@code TeamBalance}'s
 * and {@code RegionSync}'s own sync, needed for the same reason: {@code NationManager} is a plain
 * server-side {@code SavedData}, so without this no client would ever know its own team belongs
 * to a nation (e.g. to show it in the World Map info popup).
 */
public final class NationSync {

    private NationSync() {}

    public static void init() {
        // A freshly-logged-in player wouldn't otherwise see their team's nation until membership
        // next changes - matches TeamBalance's/RegionSync's own login sync for the same reason.
        TeamEvent.PLAYER_LOGGED_IN.register(event -> pushTo(event.getPlayer(), event.getTeam()));
    }

    public static void pushToTeam(MinecraftServer server, Team team) {
        String nationName = nameOf(NationManager.get(server).getNationForTeam(team.getId()));
        NationSyncPayload payload = new NationSyncPayload(team.getId(), nationName);
        for (ServerPlayer player : team.getOnlineMembers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void pushTo(ServerPlayer player, Team team) {
        String nationName = nameOf(NationManager.get(player.getServer()).getNationForTeam(team.getId()));
        PacketDistributor.sendToPlayer(player, new NationSyncPayload(team.getId(), nationName));
    }

    private static String nameOf(Nation nation) {
        return nation == null ? "" : nation.name();
    }
}
