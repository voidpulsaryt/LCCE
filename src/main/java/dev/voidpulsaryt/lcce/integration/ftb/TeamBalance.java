package dev.voidpulsaryt.lcce.integration.ftb;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.ftb.mods.ftbteams.api.property.BigIntegerProperty;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsaryt.lcce.LCCEMod;
import dev.voidpulsaryt.lcce.integration.currency.TransactionLog;
import dev.voidpulsaryt.lcce.network.TeamBalanceSyncPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigInteger;

/**
 * Every FTB Team (party or personal/solo) gets a currency balance via FTB Teams' own property
 * system - the same mechanism FTB Chunks itself uses for per-team settings. This means "team bank
 * account" and "your own funds when playing solo" are naturally the same code path: a solo
 * player's personal team just has one member.
 * <p>
 * The property itself only tracks a number; it's deliberately hidden from the normal team
 * settings GUI/editor since it should only ever move through this mod's own economy actions
 * (claim/unclaim charges, deposits, future upkeep/war billing), never edited freely by players.
 */
public final class TeamBalance {

    public static final TeamProperty<BigInteger> BALANCE = new BigIntegerProperty(
            ResourceLocation.fromNamespaceAndPath(LCCEMod.MOD_ID, "balance"),
            BigInteger.ZERO
    ).notPlayerEditable().hidden();

    private TeamBalance() {}

    public static void init() {
        TeamEvent.COLLECT_PROPERTIES.register(event -> event.add(BALANCE));
        // A freshly-logged-in player wouldn't otherwise see their team's balance until it next
        // changes - this gives them the current value immediately instead of a stale/blank one.
        TeamEvent.PLAYER_LOGGED_IN.register(event -> syncTo(event.getPlayer(), event.getTeam()));
    }

    public static BigInteger get(Team team) {
        return team.getProperty(BALANCE);
    }

    public static void set(Team team, BigInteger value) {
        BigInteger clamped = value.max(BigInteger.ZERO);
        team.setProperty(BALANCE, clamped);
        syncToOnlineMembers(team, clamped);
    }

    /**
     * FTB Teams' own property system never syncs a value to any client on its own
     * ({@code AbstractTeamBase#setProperty} only updates the server-side value and marks the team
     * dirty for disk save) - so without this, no client would ever be able to display its own
     * team's balance anywhere, e.g. the World Map info popup.
     */
    private static void syncToOnlineMembers(Team team, BigInteger balance) {
        long syncable = balance.bitLength() < 63 ? balance.longValueExact() : Long.MAX_VALUE;
        TeamBalanceSyncPayload payload = new TeamBalanceSyncPayload(team.getId(), syncable);
        for (ServerPlayer player : team.getOnlineMembers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Sends this team's current balance to one specific player - for their initial sync on login. */
    public static void syncTo(ServerPlayer player, Team team) {
        BigInteger balance = get(team);
        long syncable = balance.bitLength() < 63 ? balance.longValueExact() : Long.MAX_VALUE;
        PacketDistributor.sendToPlayer(player, new TeamBalanceSyncPayload(team.getId(), syncable));
    }

    public static boolean canAfford(Team team, BigInteger cost) {
        return get(team).compareTo(cost) >= 0;
    }

    public static void charge(Team team, BigInteger cost) {
        set(team, get(team).subtract(cost));
        TransactionLog.logTeam(team, false, cost);
    }

    public static void refund(Team team, BigInteger amount) {
        set(team, get(team).add(amount));
        TransactionLog.logTeam(team, true, amount);
    }
}
