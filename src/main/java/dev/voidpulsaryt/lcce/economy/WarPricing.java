package dev.voidpulsaryt.lcce.economy;

import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.war.War;
import dev.voidpulsaryt.lcce.war.WarManager;
import net.minecraft.server.MinecraftServer;

import java.math.BigInteger;
import java.util.UUID;

/** Per-period war upkeep cost, escalating the longer a war has been active. */
public final class WarPricing {

    private WarPricing() {}

    /** The (always-full) cost currently billed to the defender's side of this war. */
    public static BigInteger defenderCost(War war, long now, long periodTicks) {
        long periodsElapsed = periodTicks <= 0 ? 0 : Math.max(0, (now - war.declaredAtGameTime()) / periodTicks);
        double cost = LCCEConfig.WAR_BASE_UPKEEP_COST.get()
                * Math.pow(LCCEConfig.WAR_UPKEEP_GROWTH_PER_PERIOD.get(), periodsElapsed);
        if (war.isSiege()) {
            cost *= LCCEConfig.WAR_SIEGE_COST_MULTIPLIER.get();
        }
        return BigInteger.valueOf(Math.round(cost));
    }

    /** The attacker's side, which is the defender's cost scaled by {@code war.attackerCostMultiplier}. */
    public static BigInteger attackerCost(War war, long now, long periodTicks) {
        double multiplier = LCCEConfig.WAR_ATTACKER_COST_MULTIPLIER.get();
        return BigInteger.valueOf(Math.round(defenderCost(war, now, periodTicks).doubleValue() * multiplier));
    }

    /** Sum of everything a team owes this period across every war it's a part of, either side. */
    public static BigInteger totalWarCostForTeam(WarManager warManager, UUID teamId, MinecraftServer server) {
        long now = server.overworld().getGameTime();
        long periodTicks = LCCEConfig.UPKEEP_PERIOD_TICKS.get();

        BigInteger total = BigInteger.ZERO;
        for (War war : warManager.getOutgoingWars(teamId)) {
            total = total.add(attackerCost(war, now, periodTicks));
        }
        for (War war : warManager.getIncomingWars(teamId)) {
            total = total.add(defenderCost(war, now, periodTicks));
        }
        return total;
    }
}
