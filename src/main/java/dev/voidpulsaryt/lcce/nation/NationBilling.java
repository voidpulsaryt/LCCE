package dev.voidpulsaryt.lcce.nation;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.integration.ftb.TeamBalance;
import net.minecraft.server.MinecraftServer;

import java.math.BigInteger;

/**
 * Runs each upkeep period, alongside {@code UpkeepBilling}: every nation's per-member-team tax
 * (if any - {@code /lcce nation tax}) is charged from each member team's own balance into the
 * nation's own pooled balance. A member team that can't afford its tax that period simply isn't
 * taxed - nothing else about it changes, same as the per-player resident tax.
 */
public final class NationBilling {

    private NationBilling() {}

    public static void billAllNations(MinecraftServer server) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        NationManager manager = NationManager.get(server);
        for (Nation nation : manager.getAllNations()) {
            long tax = nation.taxPerMemberPerPeriod();
            if (tax <= 0) {
                continue;
            }
            BigInteger amount = BigInteger.valueOf(tax);
            BigInteger collected = BigInteger.ZERO;
            for (var memberId : nation.memberTeamIds()) {
                Team team = FTBTeamsAPI.api().getManager().getTeamByID(memberId).orElse(null);
                if (team == null || !TeamBalance.canAfford(team, amount)) {
                    continue;
                }
                TeamBalance.charge(team, amount);
                collected = collected.add(amount);
            }
            if (collected.signum() > 0) {
                nation.setBalance(nation.balance().add(collected));
                manager.markDirty();
            }
        }
    }
}
